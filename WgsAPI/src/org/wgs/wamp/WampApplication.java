package org.wgs.wamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.InitialContext;
import javax.websocket.CloseReason;
import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.Session;


public class WampApplication 
    extends javax.websocket.server.ServerEndpointConfig.Configurator
    implements javax.websocket.server.ServerEndpointConfig
{
    public  static final int WAMPv1 = 1;
    public  static final int WAMPv2 = 2;   

    private static final Logger logger = Logger.getLogger(WampApplication.class.getName());

    private int     wampVersion;
    private Class   endpointClass;
    private String  path;
    private boolean started;
    private Map<String,WampModule> modules;
    private WampModule defaultModule;
    private ExecutorService executorService;
    private ConcurrentHashMap<String,WampSocket> sockets;    
    
    private TreeMap<String,WampMethod> rpcsByName;
    private ConcurrentHashMap<String,WampCalleeRegistration> calleePatterns;
    private ConcurrentHashMap<Long,WampCalleeRegistration>   calleeRegistrationById;
    private ConcurrentHashMap<String,WampCalleeRegistration> calleeRegistrationByUri;
    
    
    
    
    public WampApplication(int version, Class endpointClass, String path)
    {
        try {
            InitialContext ctx = new InitialContext();
            executorService = (ExecutorService)ctx.lookup("concurrent/WampRpcExecutorService");
        } catch(Exception ex) { }
        
        this.wampVersion = version;
        this.endpointClass = endpointClass;
        this.path = path;
        
        this.sockets = new ConcurrentHashMap<String,WampSocket>();
        this.modules = new HashMap<String,WampModule>();
        this.rpcsByName = new TreeMap<String,WampMethod>();
        this.calleeRegistrationById = new ConcurrentHashMap<Long,WampCalleeRegistration>();
        this.calleeRegistrationByUri = new ConcurrentHashMap<String,WampCalleeRegistration>();
        this.calleePatterns = new ConcurrentHashMap<String,WampCalleeRegistration>();
        
        this.registerWampModule(WampCRA.class);
        
        this.defaultModule = new WampModule(this);
        WampServices.registerApplication(path, this);
    }
    
    public int getWampVersion() {
        return wampVersion;
    }
    
    public void setWampVersion(int version) {
        this.wampVersion = version;
    }
    
    public synchronized boolean start() {
        boolean val = started;
        started = true;
        return !val;
    }
    
    public WampSocket getWampSocket(String sessionId)
    {
        if(sessionId == null) {
            return null;
        } else {
            return sockets.get(sessionId);
        }
    }

    
    public WampModule getDefaultWampModule() {
        return defaultModule;
    }
    
    
    public WampModule getWampModule(String moduleName, WampModule defaultModule)
    {
        WampModule module = null;
        while(module == null && moduleName != null) {
            module = modules.get(normalizeModuleName(moduleName));
            if(module == null) {
                int pos = moduleName.lastIndexOf(".");
                if(pos != -1) moduleName = moduleName.substring(0, pos);
                else moduleName = null;
            }
        } 
        if(module == null) {
            module = defaultModule;
        }
        return module;
    }
    
    public String getServerId() 
    {
        return "wgs";
    }

    
    public void onWampOpen(final Session session, final EndpointConfig config) {
        System.out.println("##################### Session opened");
        
        final WampSocket clientSocket = new WampSocket(this, session);
        sockets.put(session.getId(), clientSocket);

        for(WampModule module : modules.values()) {
            try { 
                module.onConnect(clientSocket); 
            } catch(Exception ex) {
                logger.log(Level.SEVERE, "Error disconnecting socket:", ex);
            }
        }        


        session.setMaxIdleTimeout(0L);  // forever
        
        String subproto = (session.getNegotiatedSubprotocol());
        
        if(subproto != null && subproto.equalsIgnoreCase("wamp.2.msgpack")) {
            session.addMessageHandler(new MessageHandler.Whole<byte[]>() {

                @Override
                public void onMessage(byte[] message) {
                    try {
                        WampList request = (WampList)WampObject.getSerializer(WampEncoding.MsgPack).deserialize(message);
                        WampApplication.this.onWampMessage(clientSocket, request);
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
                        System.out.println("onWampMessage: " + message);
                        WampList request = (WampList)WampObject.getSerializer(WampEncoding.JSon).deserialize(message);
                        WampApplication.this.onWampMessage(clientSocket, request);
                    } catch(Exception ex) { 
                        logger.log(Level.SEVERE, "Error processing message: "+message, ex);
                    }
                }


            });

        }
        
        // Send WELCOME message to client:
        WampProtocol.sendWelcomeMessage(this, clientSocket);
       
    }   

    public void onWampMessage(WampSocket clientSocket, WampList request) throws Exception
    {
        Long requestType = request.get(0).asLong();
        //logger.log(Level.INFO, "Request type = {0}", new Object[]{requestType});

        switch(requestType.intValue()) {
            case 1:     // HELLO
                clientSocket.setVersionSupport(WampApplication.WAMPv2);                
                clientSocket.setHelloDetails((WampDict)request.get(2));
                break;
            case 2:     // GOODBYE
                clientSocket.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "goodbye received"));
                break;
            case 3:     // HEARTBEAT
                processHeartbeatMessage(clientSocket, request);
                break;                
            case 10:    // SUBSCRIBE
                Long requestId1 = request.get(1).asLong();
                WampDict subOptionsNode = (request.size() > 2) ? (WampDict)request.get(2) : null;
                WampSubscriptionOptions subOptions = new WampSubscriptionOptions(subOptionsNode);
                String subscriptionTopicName = request.get(3).asText();
                WampServices.subscribeClientWithTopic(this, clientSocket, requestId1, subscriptionTopicName, subOptions);
                break;
            case 20:    // UNSUBSCRIBE
                Long requestId2 = request.get(1).asLong();
                Long subscriptionId2 = request.get(2).asLong();
                WampServices.unsubscribeClientFromTopic(this, clientSocket, requestId2, subscriptionId2);
                break;
            case 30:    // PUBLISH
                WampServices.processPublishMessage(this, clientSocket, request);
                break;                
            case 50:    // REGISTER
                processRegisterMessage(this, clientSocket, request);
                break;
            case 60:    // UNREGISTER
                processUnregisterMessage(this, clientSocket, request);
                break;                
            case 70:    // CALL
                processCallMessage(clientSocket, request);
                break;
            case 71:    // CANCEL_CALL
                processCallCancelMessage(clientSocket, request);
                break;
            case 82:    // INVOCATION
                processInvocationProgress(clientSocket, request);
                break;
            case 83:    // INVOCATION_RESULT
                processInvocationResult(clientSocket, request);
                break;
            case 84:    // INVOCATION_ERROR
                processInvocationError(clientSocket, request);
                break;
            default:
                logger.log(Level.SEVERE, "Request type not implemented: {0}", new Object[]{requestType});
        }


    }
    
    
    public void onWampClose(Session session, CloseReason reason) 
    {
        WampSocket clientSocket = sockets.remove(session.getId());
        if(clientSocket != null) {

            try { clientSocket.close(reason); }
            catch(Exception ex) { }            
            
            for(WampModule module : modules.values()) {
                try { 
                    module.onDisconnect(clientSocket); 
                } catch(Exception ex) {
                    logger.log(Level.SEVERE, "Error disconnecting client:", ex);
                }
            }

            // First remove subscriptions to topic patterns:
            for(WampSubscription subscription : clientSocket.getSubscriptions()) {
                if(subscription.getOptions().getMatchType() != MatchEnum.exact) {  // prefix or wildcards
                    WampServices.unsubscribeClientFromTopic(this, clientSocket, null, subscription.getId());
                }
            }

            // Then, remove remaining subscriptions to single topics:
            for(WampSubscription subscription : clientSocket.getSubscriptions()) {
                WampServices.unsubscribeClientFromTopic(this, clientSocket, null, subscription.getId());
            }        
            
            logger.log(Level.INFO, "Socket disconnected: {0}", new Object[] {clientSocket.getSessionId()});
        }
        
    }
    
    public String normalizeModuleName(String moduleName) 
    {
        int schemaPos = moduleName.indexOf(":");
        if(schemaPos != -1) moduleName = moduleName.substring(schemaPos+1);
        if(!moduleName.endsWith(".")) moduleName = moduleName + ".";
        return moduleName;
    }
    
    @SuppressWarnings("unchecked")
    public void registerWampModule(Class moduleClass)
    {
        try {
            WampModule module = (WampModule)moduleClass.getConstructor(WampApplication.class).newInstance(this);
            modules.put(normalizeModuleName(module.getModuleName()), module);
        } catch(Exception ex) {
            logger.log(Level.SEVERE, "WgsEndpoint: Error registering WGS module", ex);
        }        
    }

    
    public void createRPC(String name, WampMethod rpc)
    {
        this.rpcsByName.put(name,rpc);
    }
    
    public void removeRPC(String name)
    {
        this.rpcsByName.remove(name);
    }
    
    public WampMethod getLocalRPCs(String name, WampCallOptions options)
    {

        return rpcsByName.get(name);
    }
    
    public ArrayList<WampRemoteMethod> getRemoteRPCs(String name, WampCallOptions options)
    {
        ArrayList<WampRemoteMethod> retval = new ArrayList<WampRemoteMethod>();

        String partition = null;
        if(options != null && options.getRunOn() == WampCallOptions.RunOnEnum.partition) partition = options.getPartition();

        WampCalleeRegistration reg = calleeRegistrationByUri.get(name);
        if(reg != null) {
            for(WampRemoteMethod remoteMethod : reg.getRemoteMethods()) {
                if(remoteMethod.hasPartition(partition)) {
                    retval.add(remoteMethod);
                }
            }
        }
        
        for(WampCalleeRegistration registration : calleePatterns.values()) {
            if(WampServices.isUriMatchingWithRegExp(name, registration.getRegExp())) {
                for(WampRemoteMethod remoteMethod : registration.getRemoteMethods()) {
                    if(remoteMethod.hasPartition(partition)) {
                        retval.add(remoteMethod);
                    }
                }
            }
        }
        
        return retval;
    }    
    
    public WampCalleeRegistration getRegistration(Long registrationId)
    {
        return calleeRegistrationById.get(registrationId);
    }
    
    private void processHeartbeatMessage(WampSocket clientSocket, WampList request) throws Exception
    {
        Long incomingHeartbeatSeq = request.get(1).asLong();
        Long outgoingHeartbeatSeq = request.get(1).asLong();
        clientSocket.setIncomingHeartbeat(outgoingHeartbeatSeq);        
    }
    
    private void processPrefixMessage(WampSocket clientSocket, WampList request) throws Exception
    {
        String prefix = request.get(1).asText();
        String url = request.get(2).asText();
	clientSocket.registerPrefixURL(prefix, url);
    }

    private void processHeartBeat(WampSocket clientSocket, WampList request) throws Exception
    {
        Long heartbeatSequenceNo = request.get(1).asLong();
        clientSocket.setIncomingHeartbeat(heartbeatSequenceNo);
    }

    
    public void processRegisterMessage(WampApplication app, WampSocket clientSocket, WampList request) throws Exception 
    {
        Long requestId = request.get(1).asLong();
        WampDict options = (WampDict)request.get(2);
        String methodUriOrPattern = clientSocket.normalizeURI(request.get(3).asText());
        MatchEnum matchType = MatchEnum.exact;
        if(options != null && options.has("match")) {
            matchType = MatchEnum.valueOf(options.get("match").asText().toLowerCase());
            
        }
        
        if(rpcsByName.get(methodUriOrPattern) != null) {  // Don't override system functions
            if(requestId != null) WampProtocol.sendRegisterError(clientSocket, requestId, "wamp.error.procedure_already_exists");
            throw new WampException("wamp.error.procedure_already_exists", "procedure alread exists");
        }
        
        if(matchType == MatchEnum.prefix && !methodUriOrPattern.endsWith("..")) {
            methodUriOrPattern = methodUriOrPattern + "..";
        }


            
        WampCalleeRegistration registration = calleeRegistrationByUri.get(methodUriOrPattern);
        if(registration == null) {
            Long registrationId = WampProtocol.newId();  
            registration = new WampCalleeRegistration(registrationId, matchType, methodUriOrPattern);
            calleeRegistrationById.put(registrationId, registration);
            calleeRegistrationByUri.put(methodUriOrPattern, registration);
            if(matchType != MatchEnum.exact) calleePatterns.put(methodUriOrPattern, registration);
        }               
        
        
        try {
            WampModule module = app.getDefaultWampModule();
            module.onRegister(registration.getId(), clientSocket, registration, matchType, methodUriOrPattern, request);

            if(requestId != null) WampProtocol.sendRegisteredMessage(clientSocket, requestId, registration.getId());
            
        } catch(Exception ex) {
            logger.log(Level.FINE, "Error in publishing to topic", ex);
            WampProtocol.sendRegisterError(clientSocket, requestId, "wamp.error.not_authorized");
        }
        
    }    

    public void processUnregisterMessage(WampApplication app, WampSocket clientSocket, WampList request) throws Exception 
    {
        Long requestId = request.get(1).asLong();
        Long registrationId = request.get(2).asLong();
        
        try {
            WampCalleeRegistration registration = calleeRegistrationById.get(registrationId);
            WampModule module = app.getDefaultWampModule();
            module.onUnregister(clientSocket, requestId, registrationId);
            //calleeRegistrationById.remove(registration.getProcedureURI());
            //calleeRegistrationPatterns.remove(registration.getProcedureURI());

        } catch(Exception ex) {
            logger.log(Level.FINE, "Error in publishing to topic", ex);
        }  
        
    }
    
    private void processCallCancelMessage(WampSocket clientSocket, WampList request) throws Exception
    {
        Long callID  = request.get(1).asLong();
        WampDict cancelOptions = (WampDict)request.get(2);
        WampCallController call = clientSocket.getRpcController(callID);
        call.cancel(cancelOptions);
    }
    
    private void processCallMessage(WampSocket clientSocket, WampList request) throws Exception
    {
        WampCallController call = new WampCallController(this, clientSocket, request);
        
        if(executorService == null) {
            call.run();
        }
        else {
            Future<?> future = executorService.submit(call);
            call.setFuture(future);
        }
        
    }
    
    
    private void processInvocationProgress(WampSocket providerSocket, WampList request) throws Exception
    {
        Long invocationId = request.get(1).asLong();
        Promise promise = providerSocket.getRpcPromise(invocationId);
        WampList progress = (WampList)request.get(2);
        WampDict progressKw = (WampDict)request.get(3);
        promise.progress(progress, progressKw);
    }    
    
    private void processInvocationResult(WampSocket providerSocket, WampList request) throws Exception
    {
        Long invocationId = request.get(1).asLong();
        Promise promise = providerSocket.getRpcPromise(invocationId);
        WampList result = (WampList)request.get(2);
        WampDict resultKw = (WampDict)request.get(3);
        promise.resolve(result,resultKw);
        providerSocket.removeRpcPromise(invocationId);
    }
    
    private void processInvocationError(WampSocket providerSocket, WampList request) throws Exception
    {
        Long invocationId = request.get(1).asLong();
        String errorURI = request.get(2).asText();
        WampObject exception = request.get(3);
        Promise promise = providerSocket.getRpcPromise(invocationId);
        promise.reject(errorURI, exception);
        providerSocket.removeRpcPromise(invocationId);
    }
    
    
    @Override
    public Class<?> getEndpointClass() {
        return endpointClass;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public List<String> getSubprotocols() {
        List<String> subprotocols = java.util.Arrays.asList("wamp");
        return subprotocols;
    }

    @Override
    public List<Extension> getExtensions() {
        List<Extension> extensions = Collections.emptyList();
        return extensions;
    }

    @Override
    public Configurator getConfigurator() {
        return this;
    }

    @Override
    public List<Class<? extends Encoder>> getEncoders() {
        List<Class<? extends Encoder>> encoders = Collections.emptyList();
        return encoders;
    }

    @Override
    public List<Class<? extends Decoder>> getDecoders() {
        List<Class<? extends Decoder>> decoders = Collections.emptyList();
        return decoders;
    }

    @Override
    public Map<String, Object> getUserProperties() {
        Map<String, Object> userProperties = new HashMap<String, Object>();
        return userProperties;
    }

    @Override
    public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
        String subprotocol = "wamp";
        if (requested != null) {
            for (String clientProtocol : requested) {
                if (supported.contains(clientProtocol)) {
                    subprotocol = clientProtocol;
                    break;
                }
            }
        }
        return subprotocol;
    }
    
}
