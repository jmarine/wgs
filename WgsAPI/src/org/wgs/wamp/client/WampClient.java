
package org.wgs.wamp.client;

import java.net.URI;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.WebSocketContainer;
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
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.rpc.WampCallController;
import org.wgs.wamp.rpc.WampCallOptions;
import org.wgs.wamp.rpc.WampMethod;
import org.wgs.wamp.topic.WampPublishOptions;
import org.wgs.wamp.topic.WampSubscriptionOptions;
import org.wgs.wamp.transport.http.websocket.WampEndpointConfig;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampMatchType;


public class WampClient extends Endpoint 
{
    private static final Logger logger = Logger.getLogger(WampClient.class.getName());    
    
    private URI uri;
    private String authid;
    private String password;
    private boolean open;
    private String realm;
    private WampEncoding preferredEncoding;
    private WampEncoding encoding;
    private WebSocketContainer con;
    private WampApplication wampApp;
    private WampSocket clientSocket;
    private AtomicInteger taskCount;
    private ConcurrentHashMap<Long, String> rpcRegistrationsById;
    private ConcurrentHashMap<String, Long> rpcRegistrationsByURI;
    private ConcurrentHashMap<Long, WampMethod> rpcHandlers;
    private ConcurrentHashMap<Long, String> subscriptionsById;
    private ConcurrentHashMap<String, Long> subscriptionsByTopicAndOptions;
    
    private ConcurrentHashMap<Long, WampList> pendingRequests;
    
        
    public WampClient(String uri) throws Exception
    {
        this.uri = new URI(uri);
        this.preferredEncoding = WampEncoding.JSON;
        this.pendingRequests = new ConcurrentHashMap<Long, WampList>();
        this.rpcRegistrationsById = new ConcurrentHashMap<Long, String>();
        this.rpcRegistrationsByURI = new ConcurrentHashMap<String, Long>();
        this.rpcHandlers = new ConcurrentHashMap<Long, WampMethod>();
        this.subscriptionsById = new ConcurrentHashMap<Long, String>();
        this.subscriptionsByTopicAndOptions = new ConcurrentHashMap<String, Long>();
        this.taskCount = new AtomicInteger(0);
        
        this.wampApp = new WampApplication(WampApplication.WAMPv2, null) {
            @Override
            public void registerWampModules() { }            
            
            @Override
            public void onWampMessage(final WampSocket clientSocket, WampList request) throws Exception
            {
                Long requestType = request.getLong(0);
                if(logger.isLoggable(Level.FINEST)) logger.log(Level.FINEST, "RECEIVED MESSAGE TYPE: " + requestType);

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
                        
                        break;
                        
                    case WampProtocol.WELCOME:
                        WampDict welcomeDetails = (request.size() > 2) ? (WampDict)request.get(2) : null;
                        clientSocket.setSessionId(request.getLong(1));
                        onWampWelcome(clientSocket, welcomeDetails);
                        removePendingMessage(null);
                        break;     
                        
                    case WampProtocol.CALL_RESULT:
                        Long callResponseId = request.getLong(1);
                        WampList callRequestList = WampClient.this.pendingRequests.get(callResponseId);
                        if(callRequestList == null) {
                            System.out.println("Unexpected CALL_RESULT");
                        } else {
                            Deferred callback = (Deferred)callRequestList.get(0);
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
                            Deferred registrationPromise = (Deferred)registrationParams.get(0);
                            String procedureURI = registrationParams.getText(1);
                            WampMethod implementation = wampApp.getLocalRPC(procedureURI);
                            WampClient.this.rpcRegistrationsById.put(registrationId, procedureURI);
                            WampClient.this.rpcRegistrationsByURI.put(procedureURI, registrationId);
                            WampClient.this.rpcHandlers.put(registrationId, implementation);
                            if(registrationPromise != null) registrationPromise.resolve(registrationId);
                            removePendingMessage(registeredRequestId);
                        }
                        break;
                        
                    case WampProtocol.UNREGISTERED:
                        Long unregisteredRequestId = request.getLong(1);
                        WampList unregistrationParams = WampClient.this.pendingRequests.get(unregisteredRequestId);
                        if(unregistrationParams != null) {
                            Long unregistrationId = request.getLong(2);
                            Deferred unregistrationPromise = (Deferred)unregistrationParams.get(0);
                            String procedureURI = unregistrationParams.getText(1);
                            if(unregistrationPromise != null) unregistrationPromise.resolve(unregistrationId);
                            WampClient.this.rpcHandlers.remove(unregistrationId);
                            removePendingMessage(unregisteredRequestId);
                            //delete client.rpcRegistrationsById[registrationId];
                            //delete client.rpcRegistrationsByURI[procedureURI];              
                        }                        
                        break;       
                        
                    case WampProtocol.INVOCATION:
                        final Long invocationRequestId = request.getLong(1);
                        logger.log(Level.FINEST, "RECEIVED INVOCATION ID: " + invocationRequestId);
                        try {
                            Long invocationRegistrationId = request.getLong(2);
                            WampDict details = (WampDict)request.get(3);
                            WampList arguments = (request.size() > 4) ? (WampList)request.get(4) : null;
                            WampDict argumentsKw = (request.size() > 5) ? (WampDict)request.get(5) : null;
                            WampMethod invocationCall = WampClient.this.rpcHandlers.get(invocationRegistrationId);
                            if(invocationCall != null) {
                                Long callID = request.getLong(1);
                                WampCallOptions options = new WampCallOptions(details);
                                WampCallController task = new WampCallController(this, clientSocket, callID, invocationCall.getProcedureURI(), options, arguments, argumentsKw);

                                Promise promise = invocationCall.invoke(task, clientSocket, arguments, argumentsKw, task.getOptions());
                                promise.done(new DoneCallback() {
                                    @Override
                                    public void onDone(Object result) {
                                        WampDict resultKw = new WampDict();
                                        if(result == null) {
                                            result = new WampList();
                                        } else if(result instanceof WampDict) {
                                            resultKw = (WampDict)result;
                                            result = null;
                                        } else if(!(result instanceof WampList)) {
                                            WampList list = new WampList();
                                            list.add(result);
                                            result = list;
                                        }

                                        WampDict invocationResultOptions = new WampDict();
                                        WampProtocol.sendInvocationResultMessage(clientSocket, invocationRequestId, invocationResultOptions, (WampList)result, resultKw);
                                    }
                                });
                                
                                promise.progress(new ProgressCallback() {
                                    @Override
                                    public void onProgress(Object result) {
                                        WampDict resultKw = new WampDict();
                                        if(result == null) {
                                            result = new WampList();
                                        } else if(result instanceof WampDict) {
                                            resultKw = (WampDict)result;
                                            result = null;
                                        } else if(!(result instanceof WampList)) {
                                            WampList list = new WampList();
                                            list.add(result);
                                            result = list;
                                        }

                                        WampDict invocationResultOptions = new WampDict();
                                        invocationResultOptions.put("progress", true);
                                        WampProtocol.sendInvocationResultMessage(clientSocket, invocationRequestId, invocationResultOptions, (WampList)result, resultKw);
                                    }

                                });                                
                                
                                promise.fail(new FailCallback() {
                                    @Override
                                    public void onFail(Object e) {
                                        WampDict errorDetails = new WampDict();
                                        WampProtocol.sendErrorMessage(clientSocket, WampProtocol.INVOCATION, invocationRequestId, errorDetails, "wamp.error.local_invocation_error", new WampList(), new WampDict());
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
                            Deferred publishedPromise = (Deferred)publishedParams.get(0);
                            if(publishedPromise != null) publishedPromise.resolve(publishedPublicationId);
                            removePendingMessage(publishedRequestId);
                        }
                        break;
                        
                    case WampProtocol.SUBSCRIBED:
                        Long subscribedRequestId = request.getLong(1);
                        WampList subscribedParams = WampClient.this.pendingRequests.get(subscribedRequestId);
                        if(subscribedParams != null) {
                            Long subscriptionId = request.getLong(2);
                            Deferred subscribedPromise = (Deferred)subscribedParams.get(0);
                            String topicAndOptionsKey = subscribedParams.getText(1);
                            WampSubscriptionOptions options = (WampSubscriptionOptions)subscribedParams.get(2);
                            WampClient.this.subscriptionsById.put(subscriptionId, topicAndOptionsKey); 
                            WampClient.this.subscriptionsByTopicAndOptions.put(topicAndOptionsKey, subscriptionId);
                            if(subscribedPromise != null) subscribedPromise.resolve(subscriptionId);
                            removePendingMessage(subscribedRequestId);
                        }
                        break;
                        
                    case WampProtocol.UNSUBSCRIBED:
                        Long unsubscribedRequestId = request.getLong(1);
                        WampList unsubscribedParams = WampClient.this.pendingRequests.get(unsubscribedRequestId);
                        if(unsubscribedParams != null) {
                            Deferred unsubscribedPromise = (Deferred)unsubscribedParams.get(0);
                            Long subscriptionId = unsubscribedParams.getLong(1);
                            if(unsubscribedPromise != null) unsubscribedPromise.resolve(subscriptionId);
                            removePendingMessage(unsubscribedRequestId);
                            //delete client.subscriptionsById[subscriptionId];
                            //delete client.subscriptionsByTopicAndOptions[topicAndOptionsKey];              
                        }
                        break;
                        
                    case WampProtocol.ERROR:
                        Long errorResponseId = request.getLong(1);
                        removePendingMessage(errorResponseId);
                        break;
                    default:
                        super.onWampMessage(clientSocket, request);
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
            
            public void onWampWelcome(WampSocket clientSocket, WampDict details) 
            {
                WampClient.this.authid = details.getText("authid");
                registerAllRPCs();
                
                for(WampModule module : wampApp.getWampModules()) {
                    try { 
                        module.onSessionEstablished(clientSocket, details); 
                    } catch(Exception ex) {
                        logger.log(Level.SEVERE, "Error with wamp challenge:", ex);
                    }
                }                
                
            }            

        };
    }
    
    public WampApplication getWampApplication()
    {
        return wampApp;
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
    
    private WampEncoding getWampEncodingByName(String subprotocol)
    {
        if(subprotocol != null && subprotocol.equals("wamp.2.msgpack")) {
            return WampEncoding.MsgPack;        
        } else if(subprotocol != null && subprotocol.equals("wamp.2.msgpack.batched")) {
            return WampEncoding.BatchedMsgPack;
        } else if(subprotocol != null && subprotocol.equals("wamp.2.json.batched")) {
            return WampEncoding.BatchedJSON;
        } else {
            return WampEncoding.JSON;
        }
    }
   

    
    @Override
    public void onOpen(javax.websocket.Session session, EndpointConfig config) 
    {
        this.encoding = getWampEncodingByName(session.getNegotiatedSubprotocol());
        this.clientSocket = new WampSocket(wampApp, session);

        WampEndpointConfig.addWampMessageHandlers(wampApp, session);
        
    }    
    
    @Override
    public void onClose(javax.websocket.Session session, CloseReason reason) 
    {
        WampEndpointConfig.removeWampMessageHandlers(wampApp, session, reason);
        super.onClose(session, reason);
    }
        
    
    
    public void setPreferredWampEncoding(WampEncoding preferredEncoding)
    {
        this.preferredEncoding = preferredEncoding;
    }
    
    public void connect() throws Exception
    {
        this.open = false;
        this.con = ContainerProvider.getWebSocketContainer();        
        
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().preferredSubprotocols(getPreferredSubprotocolOrder()).build();
        con.connectToServer(this, config, uri);
    }
    
    public void goodbye(String reason)
    {
        WampProtocol.sendGoodbyeMessage(clientSocket, reason, null);
    }
    
    public void hello(String realm, String user, String password, boolean digestPasswordMD5) throws Exception
    {
        WampDict authDetails = new WampDict();
        WampList authMethods = new WampList();
        
        if(user != null) {
            authDetails.put("authid", user);
            authMethods.add("wampcra");
        }
        authMethods.add("anonymous");
        authDetails.put("authmethods", authMethods);

        if(password != null && digestPasswordMD5) {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            this.password = HexUtils.byteArrayToHexString(md5.digest(password.getBytes("UTF-8")));
        } else {
            this.password = password;
        }
        
        hello(realm, authDetails);
    }
    
    public void hello(String realm, WampDict authDetails)
    {
        this.authid = null;
        if(authDetails == null) authDetails = new WampDict();
        
        WampDict publisherFeatures = new WampDict();
        publisherFeatures.put("subscriber_blackwhite_listing", true);
        publisherFeatures.put("publisher_exclusion", true);
        WampDict publisherRole = new WampDict();
        publisherRole.put("features", publisherFeatures);

        WampDict subscriberFeatures = new WampDict();
        subscriberFeatures.put("publisher_identification", true);
        subscriberFeatures.put("pattern_based_subscription", true);
        subscriberFeatures.put("subscriber_metaevents", true);
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
    
    public Promise<Long, WampException, Long> publish(String topic, WampList payload, WampDict payloadKw, WampPublishOptions options)
    {
        DeferredObject<Long, WampException, Long> deferred = new DeferredObject<Long, WampException, Long>();
        Long requestId = WampProtocol.newId();
        WampList list = new WampList();
        list.add(deferred);
        createPendingMessage(requestId, list);
        WampProtocol.sendPublishMessage(clientSocket, requestId, topic, payload, payloadKw, options.toWampObject());
        return deferred.promise();
    }

    public Promise<WampResult, WampException, WampResult> call(String procedureUri, WampList args, WampDict argsKw, WampCallOptions options)
    {
        DeferredObject<WampResult, WampException, WampResult> deferred = new DeferredObject<WampResult, WampException, WampResult>();
        Long requestId = WampProtocol.newId();
        WampList list = new WampList();
        list.add(deferred);
        createPendingMessage(requestId, list);
        WampProtocol.sendCallMessage(clientSocket, requestId, options.toWampObject(), procedureUri, args, argsKw);
        return deferred.promise();
    }
    
    public void cancelCall(Long callID, WampDict options) 
    {
        WampProtocol.sendCancelCallMessage(clientSocket, callID, options);
    }
    
    // Callee API
    private void registerAllRPCs() {
        WampList names = wampApp.getAllRpcNames(clientSocket.getRealm());
        for(int i = 0; i < names.size(); i++) {
            registerRPC(null, names.getText(i));
        }
    }
    
    private Promise<Long, WampException, Long> registerRPC(WampDict options, String procedureURI) 
    {
        DeferredObject<Long, WampException, Long> deferred = new DeferredObject<Long, WampException, Long>();
        Long requestId = WampProtocol.newId();
        WampList list = new WampList();
        list.add(deferred);        
        list.add(procedureURI);
        
        createPendingMessage(requestId, list);
        
        WampProtocol.sendRegisterMessage(clientSocket, requestId, options, procedureURI);
        return deferred.promise();        
    }
    
    private Promise<Long, WampException, Long> unregisterRPC(WampDict options, String procedureURI) 
    {
        Long registrationId = this.rpcRegistrationsByURI.get(procedureURI);  // TODO: search with options
        return unregisterRPC(registrationId);
    }
    
    private Promise<Long, WampException, Long> unregisterRPC(Long registrationId) 
    {    
        DeferredObject<Long, WampException, Long> deferred = new DeferredObject<Long, WampException, Long>();
        String procedureURI = this.rpcRegistrationsById.get(registrationId);
        Long requestId = WampProtocol.newId();
        WampList list = new WampList();
        list.add(deferred);        
        list.add(procedureURI);
        
        createPendingMessage(requestId, list);
        WampProtocol.sendUnregisterMessage(clientSocket, requestId, registrationId);
        return deferred.promise();        
    }

    private String getTopicAndOptionsKey(String topicPattern, WampSubscriptionOptions options)
    {
        if(options == null) options = new WampSubscriptionOptions(null);
        if(options.getMatchType() == WampMatchType.prefix) topicPattern = topicPattern + "..";
        return topicPattern;
    }
    
    private Long getSubscriptionIdByTopicAndOptions(String topicPattern, WampSubscriptionOptions options) {
        String topicAndOptionsKey = getTopicAndOptionsKey(topicPattern, options);
        return subscriptionsByTopicAndOptions.get(topicAndOptionsKey);
    }
    
    public void subscribe(String topicURI, WampSubscriptionOptions options, Deferred dfd) 
    {
        if(options == null) options = new WampSubscriptionOptions(null);
        if(topicURI.indexOf("..") != -1) {
            options.setMatchType(WampMatchType.wildcard);
        }
        
        String topicAndOptionsKey = getTopicAndOptionsKey(topicURI,options);        
        
        Long requestId = WampProtocol.newId();
        WampList list = new WampList();
        list.add(dfd);        
        list.add(topicAndOptionsKey);
        list.add(options);
        createPendingMessage(requestId, list);
        
        WampProtocol.sendSubscribeMessage(clientSocket, requestId, topicURI, options);
    }
    
    public void unsubscribe(String topic, WampSubscriptionOptions options, Deferred dfd) 
    {
        if(options == null) options = new WampSubscriptionOptions(null);
        if(topic.indexOf("..") != -1) {
            options.setMatchType(WampMatchType.wildcard);
        }

        Long requestId = WampProtocol.newId();
        Long unsubscriptionId = getSubscriptionIdByTopicAndOptions(topic,options);
        
        WampList list = new WampList();
        list.add(dfd);      
        list.add(unsubscriptionId);
        list.add(getTopicAndOptionsKey(topic,options));
        
        createPendingMessage(requestId, list);
        WampProtocol.sendUnsubscribeMessage(clientSocket, requestId, unsubscriptionId);
    }

   
    private void createPendingMessage(Long requestId, WampList requestData)
    {
        if(requestId != null) pendingRequests.put(requestId, requestData);
        taskCount.incrementAndGet();
    }
    
    private void removePendingMessage(Long requestId) {
        if(requestId != null) pendingRequests.remove(requestId);
        if(taskCount.decrementAndGet() <= 0) {
            synchronized(taskCount) { 
                taskCount.notifyAll();
            }
        }
    }
    
    public void waitResponses() throws Exception
    {
        synchronized(taskCount) {
            if(taskCount.get() > 0) {
                taskCount.wait();
            }
        }
    }
    
    
    public String getTopicFromEventData(Long subscriptionId, WampDict details) 
    {
        String topic = details.getText("topic");
        if(topic == null) topic = this.subscriptionsById.get(subscriptionId);
        return topic;
    }

    public void close() throws Exception {
        open = false;
        this.clientSocket.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "wamp.close.normal"));
    }
    
    
}
