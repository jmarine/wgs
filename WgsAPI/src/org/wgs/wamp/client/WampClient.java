
package org.wgs.wamp.client;

import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
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
import javax.websocket.MessageHandler;
import javax.websocket.WebSocketContainer;
import org.wgs.security.WampCRA;
import org.wgs.util.HexUtils;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.rpc.WampAsyncCallback;
import org.wgs.wamp.rpc.WampCallOptions;
import org.wgs.wamp.rpc.WampMethod;
import org.wgs.wamp.topic.WampPublishOptions;
import org.wgs.wamp.topic.WampSubscriptionOptions;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampMatchType;
import org.wgs.wamp.type.WampObject;


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
        this.preferredEncoding = WampEncoding.JSon;
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
            public void onWampMessage(WampSocket clientSocket, WampList response) throws Exception
            {
                Long responseType = response.getLong(0);
                //logger.log(Level.INFO, "Request type = {0}", new Object[]{requestType});

                switch(responseType.intValue()) {
                    case WampProtocol.ABORT:
                        removePendingMessage(null);
                        break;
                        
                    case WampProtocol.CHALLENGE:
                        String authMethod = response.getText(1);
                        WampDict challengeDetails = (WampDict)response.get(2);
                        
                        onWampChallenge(clientSocket, authMethod, challengeDetails);
                        
                        if(authMethod.equalsIgnoreCase("wampcra") && WampClient.this.password != null) {
                            MessageDigest md5 = MessageDigest.getInstance("MD5");
                            String passwordMD5 = HexUtils.byteArrayToHexString(md5.digest(WampClient.this.password.getBytes("UTF-8")));
                            String challenge = challengeDetails.getText("authchallenge");
                            String signature = WampCRA.authSignature(challenge, passwordMD5, challengeDetails);
                            WampProtocol.sendAuthenticationMessage(clientSocket, signature, null);                            
                        }
                        
                        break;
                        
                    case WampProtocol.WELCOME:
                        WampDict welcomeDetails = (response.size() > 2) ? (WampDict)response.get(2) : null;
                        clientSocket.setSessionId(response.getLong(1));
                        onWampWelcome(clientSocket, welcomeDetails);
                        removePendingMessage(null);
                        break;     
                        
                    case WampProtocol.CALL_RESULT:
                        Long callResponseId = response.getLong(1);
                        WampList callRequestList = WampClient.this.pendingRequests.get(callResponseId);
                        WampAsyncCallback callback = (WampAsyncCallback)callRequestList.get(0);
                        WampDict callResultDetails = (WampDict)response.get(2);
                        WampList callResult = (response.size() > 3) ? (WampList)response.get(3) : null;
                        WampDict callResultKw = (response.size() > 4) ? (WampDict)response.get(4) : null;
                        if(callback != null) callback.resolve(callResponseId, callResultDetails, callResult, callResultKw);
                        if(callResultDetails == null || !callResultDetails.has("receive_progress") || !callResultDetails.getBoolean("receive_progress") ) {
                            removePendingMessage(callResponseId);
                        }
                        break;
                        
                    case WampProtocol.REGISTERED:
                        Long registeredRequestId = response.getLong(1);
                        Long registrationId = response.getLong(2);
                        WampList registrationParams = WampClient.this.pendingRequests.get(registeredRequestId);
                        if(registrationParams != null) {
                            WampAsyncCallback registrationPromise = (WampAsyncCallback)registrationParams.get(0);
                            String procedureURI = registrationParams.getText(1);
                            WampMethod implementation = wampApp.getLocalRPC(procedureURI);
                            WampClient.this.rpcRegistrationsById.put(registrationId, procedureURI);
                            WampClient.this.rpcRegistrationsByURI.put(procedureURI, registrationId);
                            WampClient.this.rpcHandlers.put(registrationId, implementation);
                            if(registrationPromise != null) registrationPromise.resolve(registeredRequestId, registrationId);
                            removePendingMessage(registeredRequestId);
                        }
                        break;
                        
                    case WampProtocol.UNREGISTERED:
                        Long unregisteredRequestId = response.getLong(1);
                        WampList unregistrationParams = WampClient.this.pendingRequests.get(unregisteredRequestId);
                        if(unregistrationParams != null) {
                            Long unregistrationId = response.getLong(2);
                            WampAsyncCallback unregistrationPromise = (WampAsyncCallback)unregistrationParams.get(0);
                            String procedureURI = unregistrationParams.getText(1);
                            if(unregistrationPromise != null) unregistrationPromise.resolve(unregisteredRequestId,unregistrationId);
                            WampClient.this.rpcHandlers.remove(unregistrationId);
                            removePendingMessage(unregisteredRequestId);
                            //delete client.rpcRegistrationsById[registrationId];
                            //delete client.rpcRegistrationsByURI[procedureURI];              
                        }                        
                        break;       
                        
                    case WampProtocol.INVOCATION:
                        Long invocationRequestId = response.getLong(1);
                        try {
                            Long invocationRegistrationId = response.getLong(2);
                            WampDict details = (WampDict)response.get(3);
                            WampList arguments = (response.size() > 4) ? (WampList)response.get(4) : null;
                            WampDict argumentsKw = (response.size() > 5) ? (WampDict)response.get(5) : null;
                            WampMethod invocationCall = WampClient.this.rpcHandlers.get(invocationRegistrationId);
                            if(invocationCall != null) {
                                WampDict resultKw = new WampDict();
                                WampCallOptions options = new WampCallOptions(details);
                                Object result = invocationCall.invoke(null, clientSocket, arguments, argumentsKw, options, null);
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

                        } catch(Exception e) {
                            WampDict errorDetails = new WampDict();
                            WampProtocol.sendErrorMessage(clientSocket, WampProtocol.INVOCATION, invocationRequestId, errorDetails, "wamp.error.invalid_argument", new WampList(), new WampDict());
                        }                        
                        break;
                        
                    case WampProtocol.PUBLISHED:
                        Long publishedRequestId = response.getLong(1);
                        Long publishedPublicationId = response.getLong(2);
                        WampList publishedParams = WampClient.this.pendingRequests.get(publishedRequestId);
                        if(publishedParams != null) {    
                            WampAsyncCallback publishedPromise = (WampAsyncCallback)publishedParams.get(0);
                            if(publishedPromise != null) publishedPromise.resolve(publishedRequestId, publishedPublicationId);
                            removePendingMessage(publishedRequestId);
                        }
                        break;
                        
                    case WampProtocol.SUBSCRIBED:
                        Long subscribedRequestId = response.getLong(1);
                        WampList subscribedParams = WampClient.this.pendingRequests.get(subscribedRequestId);
                        if(subscribedParams != null) {
                            Long subscriptionId = response.getLong(2);
                            WampAsyncCallback subscribedPromise = (WampAsyncCallback)subscribedParams.get(0);
                            String topicAndOptionsKey = subscribedParams.getText(1);
                            WampSubscriptionOptions options = (WampSubscriptionOptions)subscribedParams.get(2);
                            WampClient.this.subscriptionsById.put(subscriptionId, topicAndOptionsKey); 
                            WampClient.this.subscriptionsByTopicAndOptions.put(topicAndOptionsKey, subscriptionId);
                            if(subscribedPromise != null) subscribedPromise.resolve(subscribedRequestId,subscriptionId);
                            removePendingMessage(subscribedRequestId);
                        }
                        break;
                        
                    case WampProtocol.UNSUBSCRIBED:
                        Long unsubscribedRequestId = response.getLong(1);
                        WampList unsubscribedParams = WampClient.this.pendingRequests.get(unsubscribedRequestId);
                        if(unsubscribedParams != null) {
                            WampAsyncCallback unsubscribedPromise = (WampAsyncCallback)unsubscribedParams.get(0);
                            Long subscriptionId = unsubscribedParams.getLong(1);
                            if(unsubscribedPromise != null) unsubscribedPromise.resolve(unsubscribedRequestId,subscriptionId);
                            removePendingMessage(unsubscribedRequestId);
                            //delete client.subscriptionsById[subscriptionId];
                            //delete client.subscriptionsByTopicAndOptions[topicAndOptionsKey];              
                        }
                        break;
                        
                    case WampProtocol.ERROR:
                        Long errorResponseId = response.getLong(1);
                        removePendingMessage(errorResponseId);
                        break;
                    default:
                        super.onWampMessage(clientSocket, response);
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
            return java.util.Arrays.asList("wamp.2.msgpack", "wamp.2.json");
        } else {
            return java.util.Arrays.asList("wamp.2.json", "wamp.2.msgpack");
        }
    }
    
    private WampEncoding getWampEncodingByName(String subprotocol)
    {
        if(subprotocol != null && subprotocol.equals("wamp.2.msgpack")) {
            return WampEncoding.MsgPack;
        } else {
            return WampEncoding.JSon;
        }
    }
   

    
    @Override
    public void onOpen(javax.websocket.Session session, EndpointConfig config) 
    {
        this.encoding = getWampEncodingByName(session.getNegotiatedSubprotocol());
        this.clientSocket = new WampSocket(wampApp, session);

        if(encoding == WampEncoding.MsgPack) {
            session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                    @Override
                    public void onMessage(byte[] message) {
                        try {
                            logger.log(Level.FINEST, "onWampMessage (binary)");
                            WampList request = (WampList)WampObject.getSerializer(WampEncoding.MsgPack).deserialize(message);
                            logger.log(Level.FINEST, "onWampMessage (deserialized request): " + request);
                            wampApp.onWampMessage(clientSocket, request);
                        } catch(Exception ex) { 
                            logger.log(Level.SEVERE, "Error processing message: "+message, ex);
                        }
                    }

                });        
            
        } else {

            session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(String message) {
                        try {
                            logger.log(Level.FINEST, "onWampMessage (text): " + message);
                            WampList request = (WampList)WampObject.getSerializer(WampEncoding.JSon).deserialize(message);
                            wampApp.onWampMessage(clientSocket, request);
                        } catch(Exception ex) { 
                            logger.log(Level.SEVERE, "Error processing message: "+message, ex);
                        }
                    }
                });
        }
        
    }    
    
    @Override
    public void onClose(javax.websocket.Session session, CloseReason reason) 
    {
        wampApp.onWampClose(clientSocket, reason);
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
    
    public void hello(String realm, String user, String password)    
    {
        WampDict authDetails = new WampDict();
        WampList authMethods = new WampList();
        
        if(user != null) {
            authDetails.put("authid", user);
            authMethods.add("wampcra");
        }
        authMethods.add("anonymous");
        authDetails.put("authmethods", authMethods);

        this.password = password;
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
    
    public void publish(String topic, WampList payload, WampDict payloadKw, WampPublishOptions options, WampAsyncCallback dfd)
    {
        Long requestId = WampProtocol.newId();
        WampList list = new WampList();
        list.add(dfd);
        createPendingMessage(requestId, list);
        WampProtocol.sendPublishMessage(clientSocket, requestId, topic, payload, payloadKw, options.toWampObject());
    }

    public void call(String procedureUri, WampList args, WampDict argsKw, WampDict options, WampAsyncCallback dfd)
    {
        Long requestId = WampProtocol.newId();
        WampList list = new WampList();
        list.add(dfd);
        createPendingMessage(requestId, list);
        WampProtocol.sendCallMessage(clientSocket, requestId, options, procedureUri, args, argsKw);
    }
    
    public void cancelCall(Long callID, WampDict options) 
    {
        WampProtocol.sendCancelCallMessage(clientSocket, callID, options);
    }
    
    // Callee API
    private void registerAllRPCs() {
        WampList names = wampApp.getAllRpcNames(clientSocket.getRealm());
        for(int i = 0; i < names.size(); i++) {
            registerRPC(null, names.getText(i), null);
        }
    }
    
    private void registerRPC(WampDict options, String procedureURI, WampAsyncCallback dfd) {
        Long requestId = WampProtocol.newId();
        WampList list = new WampList();
        list.add(dfd);        
        list.add(procedureURI);
        
        createPendingMessage(requestId, list);
        
        WampProtocol.sendRegisterMessage(clientSocket, requestId, options, procedureURI);
    }
    
    private void unregisterRPC(Long registrationId, WampAsyncCallback dfd) {    
        String procedureURI = this.rpcRegistrationsById.get(registrationId);
        Long requestId = WampProtocol.newId();
        WampList list = new WampList();
        list.add(dfd);        
        list.add(procedureURI);
        
        createPendingMessage(requestId, list);
        WampProtocol.sendUnregisterMessage(clientSocket, requestId, registrationId);
    }

    private void unregisterRPC(WampDict options, String procedureURI, WampAsyncCallback dfd) {
        Long registrationId = this.rpcRegistrationsByURI.get(procedureURI);  // TODO: search with options
        unregisterRPC(registrationId, dfd);
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
    
    public void subscribe(String topicURI, WampSubscriptionOptions options, WampAsyncCallback dfd) 
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
    
    public void unsubscribe(String topic, WampSubscriptionOptions options, WampAsyncCallback dfd) 
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
