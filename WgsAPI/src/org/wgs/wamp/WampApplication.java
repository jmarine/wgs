/**
 * WebSocket Message Application Protocol implementation
 *
 * @author Jordi Marine Fort 
 */

package org.wgs.wamp;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InitialContext;
import javax.websocket.CloseReason;
import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.wgs.util.MessageBroker;


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
    private ConcurrentHashMap<String,WampSocket> sockets;
    private WampModule defaultModule;
    private ExecutorService executorService;
    
    
    public WampApplication(int version, Class endpointClass, String path)
    {
        try {
            InitialContext ctx = new InitialContext();
            executorService = (ExecutorService)ctx.lookup("concurrent/WampRpcExecutorService");
        } catch(Exception ex) { }
        
        this.wampVersion = WAMPv1;
        this.endpointClass = endpointClass;
        this.path = path;
        
        this.sockets = new ConcurrentHashMap<String,WampSocket>();
        this.modules = new HashMap<String,WampModule>();
        
        this.registerWampModule(WampAPI.class);
        
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
    
    public WampModule getWampModule(String moduleBaseURI, WampModule defaultModule)
    {
        WampModule module = modules.get(normalizeNamespace(moduleBaseURI));
        if(module == null && defaultModule != null) module = defaultModule;
        return module;
    }
    
    public String getServerId() 
    {
        return "wgs";
    }

    
    public void onWampOpen(final Session session) {
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


        session.addMessageHandler(new MessageHandler.Whole<String>() {

            @Override
            public void onMessage(String message) {
                try {
                    //logger.log(Level.FINE, "onMessage: {0}", new Object[]{message});
                    ObjectMapper mapper = new ObjectMapper();
                    ArrayNode request = (ArrayNode)mapper.readTree(message);                    
                    WampApplication.this.onWampMessage(clientSocket, request);
                } catch(Exception ex) { 
                    logger.log(Level.SEVERE, "Error processing message: "+message, ex);
                }
            }
            
            
        });

        
        // Send WELCOME message to client:
        WampProtocol.sendWelcomeMessage(this, clientSocket);
       
    }   

    public void onWampMessage(WampSocket clientSocket, ArrayNode request) throws Exception
    {

        //logger.log(Level.FINE, "onWampMessage.data = {0}", new Object[]{request});

        int requestType = request.get(0).asInt();
        //logger.log(Level.INFO, "Request type = {0}", new Object[]{requestType});


        switch(requestType) {
            case 0:
                clientSocket.setVersionSupport(WampApplication.WAMPv2);
                break;
            case 1:
                if(wampVersion >= WAMPv2 && clientSocket.supportVersion(2)) {
                    processHeartBeat(clientSocket, request);
                } else {
                    processPrefixMessage(clientSocket, request);
                }
                break;
            case 2:
            case 16:
                processCallMessage(clientSocket, request);
                break;
            case 17:
                processCallCancelMessage(clientSocket, request);
                break;
            case 5:
            case 64:
                JsonNode subOptionsNode = (request.size() > 2) ? request.get(2) : null;
                WampSubscriptionOptions subOptions = new WampSubscriptionOptions(subOptionsNode);
                String subscriptionTopicName = request.get(1).asText();
                WampServices.subscribeClientWithTopic(this, clientSocket, subscriptionTopicName, subOptions);
                break;
            case 6:
            case 65:                
                JsonNode unsubOptionsNode = (request.size() > 2) ? request.get(2) : null;
                WampSubscriptionOptions unsubOptions = new WampSubscriptionOptions(unsubOptionsNode);
                String unsubscriptionTopicName = request.get(1).asText();
                WampServices.unsubscribeClientFromTopic(this, clientSocket, unsubscriptionTopicName, unsubOptions);
                break;
            case 7:
            case 66:                
                WampServices.processPublishMessage(this, clientSocket, request);
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
                    WampServices.unsubscribeClientFromTopic(this, clientSocket, subscription.getTopicUriOrPattern(), subscription.getOptions());
                }
            }

            // Then, remove remaining subscriptions to single topics:
            for(WampSubscription subscription : clientSocket.getSubscriptions()) {
                WampServices.unsubscribeClientFromTopic(this, clientSocket, subscription.getTopicUriOrPattern(), subscription.getOptions());
            }        
            
            logger.log(Level.INFO, "Socket disconnected: {0}", new Object[] {clientSocket.getSessionId()});
        }
        
    }
    
    private String normalizeNamespace(String ns) 
    {
        int schemaPos = ns.indexOf(":");
        if(schemaPos != -1) ns = ns.substring(schemaPos+1);
        if(!ns.endsWith("#")) ns = ns + "#";
        return ns;
    }
    
    @SuppressWarnings("unchecked")
    public void registerWampModule(Class moduleClass)
    {
        try {
            WampModule module = (WampModule)moduleClass.getConstructor(WampApplication.class).newInstance(this);
            modules.put(normalizeNamespace(module.getBaseURL()), module);
        } catch(Exception ex) {
            logger.log(Level.SEVERE, "WgsEndpoint: Error registering WGS module", ex);
        }        
    }

    
    private void processPrefixMessage(WampSocket clientSocket, ArrayNode request) throws Exception
    {
        String prefix = request.get(1).asText();
        String url = request.get(2).asText();
	clientSocket.registerPrefixURL(prefix, url);
    }

    private void processHeartBeat(WampSocket clientSocket, ArrayNode request) throws Exception
    {
        int heartbeatSequenceNo = request.get(1).asInt();
        clientSocket.setLastHeartBeat(heartbeatSequenceNo);
    }

    private void processCallCancelMessage(WampSocket clientSocket, ArrayNode request) throws Exception
    {
        String callID  = request.get(1).asText();
        String cancelMode = (request.size() >= 3 && request.get(2).has("cancelmode")) ? request.get(2).get("cancelmode").asText() : "killnowait";
        WampCallController call = clientSocket.getRpcController(callID);
        call.cancel(cancelMode);
    }
    
    private void processCallMessage(final WampSocket clientSocket, final ArrayNode request) throws Exception
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
    
    

    
    
    public String encodeJSON(String orig) 
    {
        if(orig == null) return "null";
        
        StringBuilder buffer = new StringBuilder(orig.length());
        buffer.append("\"");

        for (int i = 0; i < orig.length(); i++) {
            char c = orig.charAt(i);
            switch (c) {
                case '\b':
                    buffer.append("\\b");
                    break;
                case '\f':
                    buffer.append("\\f");
                    break;
                case '\n':
                    buffer.append("<br />");
                    break;
                case '\r':
                    // ignore
                    break;
                case '\t':
                    buffer.append("\\t");
                    break;
                case '\'':
                    buffer.append("\\'");
                    break;
                case '\"':
                    buffer.append("\\\"");
                    break;
                case '\\':
                    buffer.append("\\\\");
                    break;
                default:
                    buffer.append(c);
            }
        }
        buffer.append("\"");
        return buffer.toString();
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


}
