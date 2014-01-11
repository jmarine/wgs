package org.wgs.wamp;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public  static final String WAMP_ERROR_URI = "http://wamp.ws/err";

    private static final Logger logger = Logger.getLogger(WampApplication.class.getName());

    private int     wampVersion;
    private Class   endpointClass;
    private String  path;
    private boolean started;
    private Map<String,WampModule> modules;
    private WampModule defaultModule;
    private ExecutorService executorService;
    private ConcurrentHashMap<String,WampSocket> sockets;    
    private ConcurrentHashMap<Long,String> registeredProceduresUriById;
    
    
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
        this.registeredProceduresUriById = new ConcurrentHashMap<Long,String>();
        
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
            case 0:     // HELLO
                clientSocket.setVersionSupport(WampApplication.WAMPv2);                
                clientSocket.setHelloDetails((WampDict)request.get(2));
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
            case 70:
                processCallMessage(clientSocket, request);
                break;
            case 71:
                processCallCancelMessage(clientSocket, request);
                break;
            case 82:
                processInvocationProgress(clientSocket, request);
                break;
            case 83:
                processInvocationResult(clientSocket, request);
                break;
            case 84:
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
                if(subscription.getOptions().getMatchType() != WampSubscriptionOptions.MatchEnum.exact) {  // prefix or wildcards
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

    
    private void processPrefixMessage(WampSocket clientSocket, WampList request) throws Exception
    {
        String prefix = request.get(1).asText();
        String url = request.get(2).asText();
	clientSocket.registerPrefixURL(prefix, url);
    }

    private void processHeartBeat(WampSocket clientSocket, WampList request) throws Exception
    {
        Long heartbeatSequenceNo = request.get(1).asLong();
        clientSocket.setLastHeartBeat(heartbeatSequenceNo);
    }

    
    public void processRegisterMessage(WampApplication app, WampSocket clientSocket, WampList request) throws Exception 
    {
        Long registrationId = WampProtocol.newId();
        Long requestId = request.get(1).asLong();
        String procedureURI = clientSocket.normalizeURI(request.get(3).asText());
        
        try {
            WampModule module = app.getWampModule(procedureURI, app.getDefaultWampModule());
            module.onRegister(registrationId, clientSocket, procedureURI, request);
            registeredProceduresUriById.put(registrationId, procedureURI);
        } catch(Exception ex) {
            logger.log(Level.FINE, "Error in publishing to topic", ex);
        }  
    }    

    public void processUnregisterMessage(WampApplication app, WampSocket clientSocket, WampList request) throws Exception 
    {
        Long requestId = request.get(1).asLong();
        Long registrationId = request.get(2).asLong();
        
        try {
            String procedureURI = registeredProceduresUriById.get(registrationId);
            WampModule module = app.getWampModule(procedureURI, app.getDefaultWampModule());
            module.onUnregister(clientSocket, requestId, registrationId);
            registeredProceduresUriById.remove(procedureURI);
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
    
    private void processInvocationResult(WampSocket providerSocket, WampList request) throws Exception
    {
        Long invocationId = request.get(1).asLong();
        WampCallController task = providerSocket.getRpcController(invocationId);
        task.setResult((WampList)request.get(2));
        task.setResultKw((WampDict)request.get(3));
        task.sendCallResults();
        providerSocket.removeRpcController(invocationId);
    }
    
    private void processInvocationProgress(WampSocket providerSocket, WampList request) throws Exception
    {
        Long invocationId = request.get(1).asLong();
        WampCallController task = providerSocket.getRpcController(invocationId);
        WampList progress = (WampList)request.get(2);
        WampDict progressKw = (WampDict)request.get(3);
        if(task.getClientSocket().supportProgressiveCalls()) {
            WampProtocol.sendCallProgress(task.getClientSocket(), task.getCallID(), progress, progressKw);
        } else {
            task.getResult().add(progress);
            task.getResultKw().putAll(progressKw);
        }
    }    
    
    private void processInvocationError(WampSocket providerSocket, WampList request) throws Exception
    {
        Long invocationId = request.get(1).asLong();
        String errorURI = request.get(2).asText();
        WampObject exception = request.get(3);
        WampCallController task = providerSocket.getRpcController(invocationId);
        WampProtocol.sendCallError(task.getClientSocket(), task.getCallID(), errorURI, null, exception);
        providerSocket.removeRpcController(invocationId);
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
