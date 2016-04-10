package org.wgs.wamp.client;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.Session;
import org.jdeferred.Deferred;
import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.jdeferred.ProgressCallback;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.wgs.security.WampCRA;
import org.wgs.util.HexUtils;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampException;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampResult;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.annotation.WampRegisterProcedure;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.rpc.WampCallController;
import org.wgs.wamp.rpc.WampCallOptions;
import org.wgs.wamp.rpc.WampMethod;
import org.wgs.wamp.topic.WampSubscriptionOptions;
import org.wgs.wamp.transport.http.websocket.WampEndpointConfig;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampMatchType;
import org.wgs.wamp.annotation.WampSubscribe;
import org.wgs.wamp.transport.http.websocket.WampEndpoint;
import org.wgs.wamp.transport.raw.WampRawSocket;


public class WampClient
{
    private static final Logger logger = Logger.getLogger(WampClient.class.getName());    
    
    private String urls;
    private String password;
    private String helloRealm;
    private WampEncoding preferredEncoding;
    private WampEncoding encoding;
    private WampApplication wampApp;
    private WampSocket clientSocket;
    private int taskCount;

    private ConcurrentHashMap<Long, String> rpcRegistrationsById;
    private ConcurrentHashMap<String, Long> rpcRegistrationsByURI;
    private ConcurrentHashMap<Long, WampMethod> rpcHandlers;
    private ConcurrentHashMap<Long, String> topicPatternsBySubscriptionId;
    
    private ConcurrentHashMap<Long, WampList> pendingRequests;
    private ConcurrentHashMap<Long, WampCallController> pendingInvocations;
    
        
    public WampClient(String urls) throws Exception
    {
        this.urls = urls;
        this.preferredEncoding = WampEncoding.JSON;
        this.pendingRequests = new ConcurrentHashMap<Long, WampList>();
        this.pendingInvocations = new ConcurrentHashMap<Long, WampCallController>();
        this.rpcRegistrationsById = new ConcurrentHashMap<Long, String>();
        this.rpcRegistrationsByURI = new ConcurrentHashMap<String, Long>();
        this.rpcHandlers = new ConcurrentHashMap<Long, WampMethod>();
        this.topicPatternsBySubscriptionId = new ConcurrentHashMap<Long, String>();

        
        this.wampApp = new WampApplication(WampApplication.WAMPv2, null) {
            
            private AtomicBoolean started = new AtomicBoolean(false);
            
            @Override
            public void registerWampModules() { }   
            
            @Override
            public boolean start() {
                return !started.getAndSet(true);  // disable router-to-router clustering registration
            }
            
            @Override
            public void onWampMessage(final WampSocket clientSocket, WampList request) throws Exception
            {
                Long requestType = request.getLong(0);
                if(logger.isLoggable(Level.FINE)) logger.log(Level.FINE, "RECEIVED MESSAGE TYPE: " + requestType + ": " + request.toString());

              try {
                switch(requestType.intValue()) {
                    case WampProtocol.ABORT:
                        removePendingMessage(null);
                        break;
                        
                    case WampProtocol.CHALLENGE:
                        String authMethod = request.getText(1);
                        WampDict challengeDetails = (WampDict)request.get(2);
                        
                        onWampChallenge(clientSocket, authMethod, challengeDetails);
                        
                        if(authMethod.equalsIgnoreCase("wampcra") && WampClient.this.password != null) {
                            String challenge = challengeDetails.getText("challenge");
                            String signature = WampCRA.authSignature(challenge, WampClient.this.password, challengeDetails);
                            WampProtocol.sendAuthenticationMessage(clientSocket, signature, null);                            
                        }
                        else
                        if(authMethod.equalsIgnoreCase("ticket") && WampClient.this.password != null) {
                            WampProtocol.sendAuthenticationMessage(clientSocket, WampClient.this.password, null);
                        }
                        
                        break;
                        
                    case WampProtocol.WELCOME:
                        WampDict welcomeDetails = (request.size() > 2) ? (WampDict)request.get(2) : null;
                        onWampSessionEstablished(clientSocket, request.getLong(1), welcomeDetails);
                        removePendingMessage(null);
                        break;     
                        
                    case WampProtocol.CALL_RESULT:
                        Long callResponseId = request.getLong(1);
                        WampList callRequestList = WampClient.this.pendingRequests.get(callResponseId);
                        if(callRequestList == null) {
                            System.out.println("Unexpected CALL_RESULT");
                        } else {
                            Deferred<WampResult, WampException, WampResult> callback = getDeferredWampResult(callRequestList);
                            WampResult result = new WampResult(callResponseId);
                            result.setDetails((WampDict)request.get(2));
                            result.setArgs((request.size() > 3) ? (WampList)request.get(3) : null);
                            result.setArgsKw((request.size() > 4) ? (WampDict)request.get(4) : null);
                            if(callback != null) callback.resolve(result);
                            if(!result.isProgressResult()) {
                                removePendingMessage(callResponseId);
                            }
                        }
                        break;
                        
                    case WampProtocol.REGISTERED:
                        Long registeredRequestId = request.getLong(1);
                        Long registrationId = request.getLong(2);
                        WampList registrationParams = WampClient.this.pendingRequests.get(registeredRequestId);
                        if(registrationParams != null) {
                            String procedureURI = registrationParams.getText(1);
                            Deferred<Long, WampException, Long> registrationPromise = getDeferredLong(registrationParams);
                            WampMethod implementation = (WampMethod)registrationParams.get(2);
                            WampClient.this.rpcRegistrationsById.put(registrationId, procedureURI);
                            WampClient.this.rpcRegistrationsByURI.put(procedureURI, registrationId);
                            if(implementation != null) WampClient.this.rpcHandlers.put(registrationId, implementation);
                            if(registrationPromise != null) registrationPromise.resolve(registrationId);
                            removePendingMessage(registeredRequestId);
                        }
                        break;
                        
                    case WampProtocol.UNREGISTERED:
                        Long unregisteredRequestId = request.getLong(1);
                        WampList unregistrationParams = WampClient.this.pendingRequests.get(unregisteredRequestId);
                        if(unregistrationParams != null) {
                            Deferred<Long, WampException, Long> unregistrationPromise = getDeferredLong(unregistrationParams);
                            Long unregistrationId = unregistrationParams.getLong(2);
                            if(unregistrationPromise != null) unregistrationPromise.resolve(unregistrationId);
                            WampClient.this.rpcHandlers.remove(unregistrationId);
                            removePendingMessage(unregisteredRequestId);
                            //delete client.rpcRegistrationsById[registrationId];
                            //delete client.rpcRegistrationsByURI[procedureURI];              
                        }                        
                        break;       
                    
                    case WampProtocol.INTERRUPT:
                        Long interruptedInvocationId = request.getLong(1);
                        if(interruptedInvocationId != null) {
                            WampCallController task = WampClient.this.pendingInvocations.remove(interruptedInvocationId);                        
                            if(task != null) {
                                WampDict interruptOptions = (WampDict)request.get(2);
                                task.cancel(interruptOptions, null);
                                removePendingMessage(null);
                            }
                        }
                        break;
                        
                    case WampProtocol.INVOCATION:
                        createPendingMessage(null, null);
                        final Long invocationRequestId = request.getLong(1);
                        //System.out.println("RECEIVED INVOCATION ID: " + invocationRequestId);
                        try {
                            // int i = 0/0;  // DEBUG: force exception, to test router send invocation interrupts to other pending callees
                            final Long invocationRegistrationId = request.getLong(2);
                            WampDict details = (WampDict)request.get(3);
                            WampList arguments = (request.size() > 4) ? (WampList)request.get(4) : null;
                            WampDict argumentsKw = (request.size() > 5) ? (WampDict)request.get(5) : null;
                            WampMethod invocationCall = WampClient.this.rpcHandlers.get(invocationRegistrationId);
                            if(invocationCall == null || clientSocket.getWampSessionId() == null) {
                                try { 
                                    WampProtocol.sendErrorMessage(clientSocket, WampProtocol.INVOCATION, invocationRequestId, new WampDict(), "wamp.error.unknown_rpc_handler", new WampList(), new WampDict()); 
                                } catch(Exception ex) { 
                                    System.out.println("SEVERE: sendInvocationResultMessage: error in handler or session: " + ex.getMessage());
                                } finally {
                                    removePendingMessage(null);
                                }
                            } else {
                                WampCallOptions options = new WampCallOptions(details);
                                WampCallController task = new WampCallController(this, clientSocket, invocationRequestId, invocationCall.getProcedureURI(), options, arguments, argumentsKw);
                                WampClient.this.pendingInvocations.put(invocationRequestId, task);
                                

                                Promise<WampResult, WampException, WampResult> promise = invocationCall.invoke(task, clientSocket, arguments, argumentsKw, task.getOptions());
                                promise.done(new DoneCallback<WampResult>() {
                                    @Override
                                    public void onDone(WampResult result) {
                                        try {
                                            WampProtocol.sendInvocationResultMessage(clientSocket, invocationRequestId, result.getDetails(), result.getArgs(), result.getArgsKw());
                                        } catch(Exception ex) { 
                                            System.out.println("SEVERE: sendInvocationResultmessage: error: " + ex.getMessage());
                                        } finally {
                                            WampClient.this.pendingInvocations.remove(invocationRequestId);
                                            removePendingMessage(null);
                                        }
                                    }
                                });
                                
                                promise.progress(new ProgressCallback<WampResult>() {
                                    @Override
                                    public void onProgress(WampResult progress) {
                                        try {
                                            WampProtocol.sendInvocationResultMessage(clientSocket, invocationRequestId, progress.getDetails(), progress.getArgs(), progress.getArgsKw());
                                        } catch(Exception ex) { 
                                            System.out.println("SEVERE: sendInvocationResultmessage: error: " + ex.getMessage());
                                        }
                                    }

                                });                                
                                
                                promise.fail(new FailCallback<WampException>() {
                                    @Override
                                    public void onFail(WampException ex) {
                                        WampProtocol.sendErrorMessage(clientSocket, WampProtocol.INVOCATION, invocationRequestId, ex.getDetails(), "wamp.error.local_invocation_error", ex.getArgs(), ex.getArgsKw());
                                        WampClient.this.pendingInvocations.remove(invocationRequestId);
                                        removePendingMessage(null);
                                    }
                                });

                            }


                        } catch(Exception e) {
                            WampDict errorDetails = new WampDict();
                            WampProtocol.sendErrorMessage(clientSocket, WampProtocol.INVOCATION, invocationRequestId, errorDetails, "wamp.error.invalid_argument", new WampList(), new WampDict());
                        }                        
                        break;
                        
                    case WampProtocol.PUBLISHED:
                        Long publishedRequestId = request.getLong(1);
                        Long publishedPublicationId = request.getLong(2);
                        WampList publishedParams = WampClient.this.pendingRequests.get(publishedRequestId);
                        if(publishedParams != null) {    
                            Deferred<Long, WampException, Long> publishedPromise = getDeferredLong(publishedParams);
                            if(publishedPromise != null) publishedPromise.resolve(publishedPublicationId);
                            removePendingMessage(publishedRequestId);
                        }
                        break;
                        
                    case WampProtocol.SUBSCRIBED:
                        Long subscribedRequestId = request.getLong(1);
                        WampList subscribedParams = WampClient.this.pendingRequests.get(subscribedRequestId);
                        if(subscribedParams != null) {
                            Long subscriptionId = request.getLong(2);
                            Deferred<Long, WampException, Long> subscribedPromise = getDeferredLong(subscribedParams);
                            String topicAndOptionsKey = subscribedParams.getText(1);
                            WampClient.this.topicPatternsBySubscriptionId.put(subscriptionId, topicAndOptionsKey); 
                            if(subscribedPromise != null) subscribedPromise.resolve(subscriptionId);
                            removePendingMessage(subscribedRequestId);
                        }
                        break;
                        
                    case WampProtocol.UNSUBSCRIBED:
                        Long unsubscribedRequestId = request.getLong(1);
                        WampList unsubscribedParams = WampClient.this.pendingRequests.get(unsubscribedRequestId);
                        if(unsubscribedParams != null) {
                            Deferred<Long, WampException, Long> unsubscribedPromise = getDeferredLong(unsubscribedParams);
                            Long subscriptionId = unsubscribedParams.getLong(1);
                            if(unsubscribedPromise != null) unsubscribedPromise.resolve(subscriptionId);
                            removePendingMessage(unsubscribedRequestId);
                            //delete client.subscriptionsById[subscriptionId];
                        }
                        break;
                        
                    case WampProtocol.ERROR:
                        logger.severe("ERROR: " + request.toString());
                        Long errorResponseId = request.getLong(2);
                        removePendingMessage(errorResponseId);
                        break;
                        
                    default:
                        super.onWampMessage(clientSocket, request);
                }
                
              } catch(Exception err) {
                  System.out.println("SEVERE ERROR: " + err.getMessage());
                  err.printStackTrace();
                  throw err;
              }
                
            }
            
            public void onWampChallenge(WampSocket clientSocket, String authMethod, WampDict details) 
            {
                for(WampModule module : wampApp.getWampModules()) {
                    try { 
                        module.onChallenge(clientSocket, authMethod, details); 
                    } catch(Exception ex) {
                        logger.log(Level.SEVERE, "Error with wamp challenge:", ex);
                    }
                }                
            }
            
            public void onWampSessionEstablished(WampSocket clientSocket, Long sessionId, WampDict details) 
            {
                clientSocket.setRealm(helloRealm);

                super.onWampSessionEstablished(clientSocket, sessionId, details);
                
                try {
                    processRegisteredAnnotations();
                    processSubscribedAnnotations();
                } catch(Exception ex) {
                    System.out.println("SEVERE: WampClient.onWampSessionEstablished: error: "  + ex.getMessage());
                    ex.printStackTrace();
                }
            }            

        };
    }
    
    public WampApplication getWampApplication()
    {
        return wampApp;
    }
    
    public WampSocket getWampSocket()
    {
        return clientSocket;
    }
    
    public boolean isOpen()
    {
        return clientSocket != null && clientSocket.isOpen();
    }
    
    @SuppressWarnings("unchecked")
    private Deferred<Long, WampException, Long> getDeferredLong(WampList request)
    {
        return (Deferred<Long, WampException, Long>)request.get(0);
    }
    
    
    @SuppressWarnings("unchecked")
    private Deferred<WampResult, WampException, WampResult> getDeferredWampResult(WampList request)
    {
        return (Deferred<WampResult, WampException, WampResult>)request.get(0);
    }
    


    private List<String> getPreferredSubprotocolOrder()
    {
        if(preferredEncoding != null && preferredEncoding == WampEncoding.MsgPack) {
            return java.util.Arrays.asList("wamp.2.msgpack", "wamp.2.msgpack.batched", "wamp.2.json", "wamp.2.json.batched");
        } else if(preferredEncoding != null && preferredEncoding == WampEncoding.BatchedMsgPack) {
            return java.util.Arrays.asList("wamp.2.msgpack.batched", "wamp.2.msgpack", "wamp.2.json.batched", "wamp.2.json");
        } else if(preferredEncoding != null && preferredEncoding == WampEncoding.BatchedJSON) {
            return java.util.Arrays.asList("wamp.2.json.batched", "wamp.2.json", "wamp.2.msgpack.batched", "wamp.2.msgpack");
        } else {
            return java.util.Arrays.asList("wamp.2.json", "wamp.2.json.batched", "wamp.2.msgpack", "wamp.2.msgpack.batched");
        }
    }
    
  
   
    public void setPreferredWampEncoding(WampEncoding preferredEncoding)
    {
        this.preferredEncoding = preferredEncoding;
    }
    
    public void connect() throws Exception
    {
        boolean opened = false;
        Exception lastError = null;
        StringTokenizer stk = new StringTokenizer(urls, ",;");
        while(!opened && stk.hasMoreTokens()) {
            try {
                URI uri = new URI(stk.nextToken());
                switch(uri.getScheme()) {
                    case "ws":
                    case "wss":
                        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().preferredSubprotocols(getPreferredSubprotocolOrder()).build();
                        config.getUserProperties().put(WampEndpointConfig.WAMP_ENDPOINTCONFIG_PROPERTY_NAME, 
                                                       new WampEndpointConfig(WampEndpoint.class, wampApp));
                        Session session = ContainerProvider.getWebSocketContainer().connectToServer(WampEndpoint.class, config, uri);
                        Long socketId = (Long)session.getUserProperties().get("_socketId");
                        clientSocket = wampApp.getSocketById(socketId);
                        break;

                    case "tcp":
                    case "ssl":
                        clientSocket = new WampRawSocket(wampApp, uri, preferredEncoding);
                        break;

                    case "http":
                    case "https":
                        throw new WampException(null, "wamp.error.protocol_not_implemented", null, null);

                }

                opened = true;

            } catch(Exception ex) {
                lastError = ex;
            }
        }

        if(!opened) throw lastError;

    }
    

    public void goodbye(String reason)
    {
        if(clientSocket.getWampSessionId() != null) {
            for(WampModule module : wampApp.getWampModules()) {
                module.onWampSessionEnd(clientSocket); 
            }

            WampProtocol.sendGoodbyeMessage(clientSocket, reason, null);
            clientSocket.setWampSessionId(null);
        }
    }
    
    public void hello(String realm, String user, String password, boolean digestPasswordMD5) throws Exception
    {
        WampDict authDetails = new WampDict();
        WampList authMethods = new WampList();
        
        if(user != null && user.length() > 0) {
            authDetails.put("authid", user);
            authMethods.add("wampcra");
        }
        authMethods.add("anonymous");
        authDetails.put("authmethods", authMethods);

        if(password != null && digestPasswordMD5) {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            this.password = HexUtils.byteArrayToHexString(md5.digest(password.getBytes(StandardCharsets.UTF_8)));
        } else {
            this.password = password;
        }
        
        hello(realm, authDetails);
    }
    
    public void hello(String realm, WampDict authDetails) throws Exception
    {
        if(authDetails == null) authDetails = new WampDict();
        this.helloRealm = realm;
        
        if(authDetails.has("ticket")) {
            this.password = authDetails.getText("ticket");
        }
        
        WampDict publisherFeatures = new WampDict();
        publisherFeatures.put("subscriber_blackwhite_listing", true);
        publisherFeatures.put("publisher_exclusion", true);
        WampDict publisherRole = new WampDict();
        publisherRole.put("features", publisherFeatures);

        WampDict subscriberFeatures = new WampDict();
        subscriberFeatures.put("publisher_identification", true);
        subscriberFeatures.put("pattern_based_subscription", true);
        //subscriberFeatures.put("subscriber_metaevents", true);
        WampDict subscriberRole = new WampDict();
        subscriberRole.put("features", subscriberFeatures);

        WampDict callerFeatures = new WampDict();
        callerFeatures.put("caller_identification", true);
        callerFeatures.put("call_canceling", true);
        callerFeatures.put("progressive_call_results", true);
        WampDict callerRole = new WampDict();
        callerRole.put("features", callerFeatures);

        WampDict calleeFeatures = new WampDict();
        calleeFeatures.put("caller_identification", true);
        calleeFeatures.put("pattern_based_registration", true);
        WampDict calleeRole = new WampDict();
        calleeRole.put("features", calleeFeatures);
        
        WampDict roles = new WampDict();
        roles.put("publisher", publisherRole);
        roles.put("subscriber", subscriberRole);
        roles.put("caller", callerRole);
        roles.put("callee", calleeRole);
        
        authDetails.put("agent", "wgs-client-java-2.0");
        authDetails.put("roles", roles);
        
        createPendingMessage(null, null);
        WampProtocol.sendHelloMessage(clientSocket, realm, authDetails);

    }
    
    public Promise<Long, WampException, Long> publish(String topic, WampList payload, WampDict payloadKw, WampDict options) throws Exception
    {
        DeferredObject<Long, WampException, Long> deferred = new DeferredObject<Long, WampException, Long>();
        Long requestId = WampProtocol.newSessionScopeId(clientSocket);
        WampList list = new WampList();
        list.add(deferred);
        if(options.has("acknowledge") && options.getBoolean("acknowledge")) createPendingMessage(requestId, list);
        WampProtocol.sendPublishMessage(clientSocket, requestId, topic, payload, payloadKw, options);
        return deferred.promise();
    }

    public Promise<WampResult, WampException, WampResult> call(String procedureUri, WampList args, WampDict argsKw, WampCallOptions options) throws Exception
    {
        DeferredObject<WampResult, WampException, WampResult> deferred = new DeferredObject<WampResult, WampException, WampResult>();
        Long requestId = WampProtocol.newSessionScopeId(clientSocket);
        WampList list = new WampList();
        list.add(deferred);
        createPendingMessage(requestId, list);
        WampProtocol.sendCallMessage(clientSocket, requestId, options.toWampObject(), procedureUri, args, argsKw);
        return deferred.promise();
    }
    
    public void cancelCall(Long callID, WampDict options) throws Exception
    {
        WampProtocol.sendCancelCallMessage(clientSocket, callID, options);
    }
    

    private void processSubscribedAnnotations() throws Exception {
        for(final WampModule module : wampApp.getWampModules()) {
            for(final Method method : module.getClass().getMethods()) {
                WampSubscribe subscription = method.getAnnotation(WampSubscribe.class);
                if(subscription != null) {
                    WampSubscriptionOptions subOpt = new WampSubscriptionOptions(null);
                    subOpt.setMatchType(subscription.match());
                    subOpt.setMetaTopics(new WampList((Object[])subscription.metatopics()));
                    subOpt.setEventsEnabled(!subscription.metaonly());
                    subscribe(subscription.topic(), subOpt).done(new DoneCallback<Long>() {
                        @Override
                        public void onDone(Long subscriptionId) {
                            module.addSubscriptionMethod(subscriptionId, method);
                        }
                    });
                }
            }
        }
    }
    

    private void processRegisteredAnnotations() throws Exception {
        for(final WampModule module : wampApp.getWampModules()) {
            String moduleName = module.getModuleName();
            for(final Method method : module.getClass().getMethods()) {
                WampRegisterProcedure registration = method.getAnnotation(WampRegisterProcedure.class);
                if(registration != null) {
                    WampDict options = new WampDict();
                    options.put("match", registration.match().toString());
                    String rpcName = moduleName + "." + registration.name();
                    registerRPC(options, rpcName, wampApp.getLocalRPC(registration.match(), rpcName));
                }
            }
        }
    }
    
    public Promise<Long, WampException, Long> registerRPC(WampDict options, String procedureURI, WampMethod implementation) throws Exception
    {
        DeferredObject<Long, WampException, Long> deferred = new DeferredObject<Long, WampException, Long>();
        Long requestId = WampProtocol.newSessionScopeId(clientSocket);
        WampList list = new WampList();
        list.add(deferred);        
        list.add(procedureURI);
        list.add(implementation);
        
        createPendingMessage(requestId, list);
        
        WampProtocol.sendRegisterMessage(clientSocket, requestId, options, procedureURI);
        
        return deferred.promise();        
    }
    
    public Promise<Long, WampException, Long> unregisterRPC(String procedureURI) throws Exception
    {
        Long registrationId = this.rpcRegistrationsByURI.get(procedureURI);  // TODO: search with options
        return unregisterRPC(registrationId);
    }
    
    protected Promise<Long, WampException, Long> unregisterRPC(Long registrationId) throws Exception
    {    
        DeferredObject<Long, WampException, Long> deferred = new DeferredObject<Long, WampException, Long>();
        String procedureURI = this.rpcRegistrationsById.get(registrationId);
        Long requestId = WampProtocol.newSessionScopeId(clientSocket);
        WampList list = new WampList();
        list.add(deferred);        
        list.add(procedureURI);
        list.add(registrationId);
        
        createPendingMessage(requestId, list);
        WampProtocol.sendUnregisterMessage(clientSocket, requestId, registrationId);
        return deferred.promise();        
    }

    private String getTopicPattern(String topicPattern, WampSubscriptionOptions options)
    {
        if(options == null) options = new WampSubscriptionOptions(null);
        if(options.getMatchType() == WampMatchType.prefix) topicPattern = topicPattern + "..";
        return topicPattern;
    }
    
    
    public Promise<Long, WampException, Long> subscribe(String topicURI, WampSubscriptionOptions options) throws Exception
    {
        DeferredObject<Long, WampException, Long> deferred = new DeferredObject<Long, WampException, Long>();        
        if(options == null) options = new WampSubscriptionOptions(null);
        if(topicURI.indexOf("..") != -1) {
            options.setMatchType(WampMatchType.wildcard);
        }
        
        String topicAndOptionsKey = getTopicPattern(topicURI,options);        

        Long requestId = WampProtocol.newSessionScopeId(clientSocket);
        WampList list = new WampList();
        list.add(deferred);        
        list.add(topicAndOptionsKey);
        list.add(options);
        createPendingMessage(requestId, list);
        
        WampProtocol.sendSubscribeMessage(clientSocket, requestId, topicURI, options);
        return deferred.promise();
    }
    
    public Promise<Long, WampException, Long> unsubscribe(Long subscriptionId) throws Exception
    {
        DeferredObject<Long, WampException, Long> deferred = new DeferredObject<Long, WampException, Long>();        

        Long requestId = WampProtocol.newSessionScopeId(clientSocket);
        
        WampList list = new WampList();
        list.add(deferred);      
        list.add(subscriptionId);
        
        createPendingMessage(requestId, list);
        WampProtocol.sendUnsubscribeMessage(clientSocket, requestId, subscriptionId);
        return deferred.promise();
    }
    
    
    private void createPendingMessage(Long requestId, WampList requestData)
    {
        synchronized(pendingRequests) {
            if(requestId != null) pendingRequests.put(requestId, requestData);
            taskCount++;
        }
    }
    
    private void removePendingMessage(Long requestId) 
    {
        synchronized(pendingRequests) {
            if(requestId != null) pendingRequests.remove(requestId);
            taskCount--;
        
            if(taskCount <= 0) {
                pendingRequests.notifyAll();
            }
        }
    }
    
    public void waitResponses() throws Exception
    {
        synchronized(pendingRequests) {
            while(taskCount > 0) {
                pendingRequests.wait();
            }
        }
    }
    
    
    public String getTopicFromEventData(Long subscriptionId, WampDict details) 
    {
        String topic = details.getText("topic");
        if(topic == null) topic = this.topicPatternsBySubscriptionId.get(subscriptionId);
        return topic;
    }
    

    public void close() throws Exception {
        clientSocket.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "wamp.close.normal"));
    }
    
    
}
