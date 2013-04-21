/**
 * WebSocket Message Application Protocol implementation
 *
 * @author Jordi Marine Fort 
 */

package org.wampservices;


import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
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
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;


public class WampApplication 
    extends javax.websocket.server.ServerEndpointConfig.Configurator
    implements javax.websocket.server.ServerEndpointConfig
{
    private static final Logger logger = Logger.getLogger(WampApplication.class.getName());

    public  static final String WAMPSERVICES_BASE_URL = "https://wampservices.org";
    
    private Class   endpointClass;
    private String  path;
    private boolean started;
    private Map<String,WampModule> modules;
    private TreeMap<String,WampTopic> topics;
    private TreeMap<String,WampTopicPattern> topicPatterns;
    private ConcurrentHashMap<Session,WampSocket> sockets;
    private WampModule defaultModule;
    private ExecutorService executorService;
    
    
    private static Map<String,WampApplication> contexts = new HashMap<String,WampApplication>();
    
    public static synchronized WampApplication getApplication(String context) 
    {
        WampApplication app = contexts.get(context);
        if(app == null) {
            app = new WampApplication(WampEndpoint.class, context);
            contexts.put(context, app);
        }
        return app;
    }
    
    
    public WampApplication(Class endpointClass, String path)
    {
        try {
            InitialContext ctx = new InitialContext();
            executorService = (ExecutorService)ctx.lookup("concurrent/WampRpcExecutorService");
        } catch(Exception ex) { }
        
        this.endpointClass = endpointClass;
        this.path = path;
        
        this.sockets = new ConcurrentHashMap<Session,WampSocket>();
        this.modules = new HashMap<String,WampModule>();
        this.topics = new TreeMap<String,WampTopic>();
        this.topicPatterns = new TreeMap<String,WampTopicPattern>();
        
        this.registerWampModule(WampAPI.class);
        
        this.defaultModule = new WampModule(this) {
            @Override
            public String getBaseURL() {
                return WAMPSERVICES_BASE_URL;
            }
        };
    }
    
    public synchronized boolean start() {
        boolean val = started;
        started = true;
        return !val;
    }
    
    private WampSocket getWampSocket(Session session)
    {
        return sockets.get(session);
    }

    
    public WampModule getWampModule(String moduleBaseURI) 
    {
        WampModule module = modules.get(moduleBaseURI);
        if(module == null) module = defaultModule;
        return module;
    }
    
    public String getServerId() {
        return "wsAppManager";
    }

    
    public void onWampOpen(final Session session) {
        System.out.println("##################### Session opened");
        
        final WampSocket clientSocket = new WampSocket(this, session);
        sockets.put(session, clientSocket);
        
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
                    logger.log(Level.FINE, "onMessage: {0}", new Object[]{message});
                    ObjectMapper mapper = new ObjectMapper();
                    ArrayNode request = (ArrayNode)mapper.readTree(message);                    
                    WampApplication.this.onWampMessage(clientSocket, request);
                } catch(Exception ex) { 
                    System.out.println("Error processing message: " + ex);
                }
            }
            
            
        });

        
        // Send WELCOME message to client:
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode response = mapper.createArrayNode();
        ObjectNode helloDetails = mapper.createObjectNode();
        response.add(0);  // WELCOME message code
        response.add(clientSocket.getSessionId());
        response.add(helloDetails);  // WAMP v2
        //response.add(1);  // WAMP v1
        //response.add(getServerId());
        clientSocket.sendWampResponse(response);
        
    }   

    public void onWampMessage(WampSocket clientSocket, ArrayNode request) throws Exception
    {

        logger.log(Level.FINE, "onWampMessage.data = {0}", new Object[]{request});

        int requestType = request.get(0).asInt();
        logger.log(Level.INFO, "Request type = {0}", new Object[]{requestType});


        switch(requestType) {
            case 0:
                clientSocket.setVersionSupport(WampSocket.WAMPv2);
                break;
            case 1:
                if(clientSocket.supportVersion(2)) {
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
                subscribeClientWithTopic(clientSocket, subscriptionTopicName, subOptions);
                break;
            case 6:
            case 65:                
                JsonNode unsubOptionsNode = (request.size() > 2) ? request.get(2) : null;
                WampSubscriptionOptions unsubOptions = new WampSubscriptionOptions(unsubOptionsNode);
                String unsubscriptionTopicName = request.get(1).asText();
                unsubscribeClientFromTopic(clientSocket, unsubscriptionTopicName, unsubOptions);
                break;
            case 7:
            case 66:                
                processPublishMessage(clientSocket, request);
                break;                
            default:
                logger.log(Level.SEVERE, "Request type not implemented: {0}", new Object[]{requestType});
        }


    }
    
    
    public void onWampClose(Session session, CloseReason reason) 
    {
        WampSocket clientSocket = sockets.remove(session);
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
                if(isTopicUriWithWildcards(subscription.getTopicUriOrPattern())) {
                    unsubscribeClientFromTopic(clientSocket, subscription.getTopicUriOrPattern(), subscription.getOptions());
                }
            }

            // Then, remove remaining subscriptions to single topics:
            for(WampSubscription subscription : clientSocket.getSubscriptions()) {
                unsubscribeClientFromTopic(clientSocket, subscription.getTopicUriOrPattern(), subscription.getOptions());
            }        
            
            logger.log(Level.INFO, "Socket disconnected: {0}", new Object[] {clientSocket.getSessionId()});
        }
        
    }
    
    @SuppressWarnings("unchecked")
    public void registerWampModule(Class moduleClass)
    {
        try {
            WampModule module = (WampModule)moduleClass.getConstructor(WampApplication.class).newInstance(this);
            modules.put(module.getBaseURL(), module);
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
        // TODO: CancelOptions
        String callID  = request.get(1).asText();
        clientSocket.cancelRpcFutureResult(callID);
    }
    
    private void processCallMessage(final WampSocket clientSocket, final ArrayNode request) throws Exception
    {
        final int callMsgType = request.get(0).asInt();
        final int callResponseMsgType = (callMsgType == 2) ? 3 : 32;
        final int callErrorMsgType = (callMsgType == 2) ? 4 : 34;                    
        
        final String callID  = request.get(1).asText();
        if(callID == null || callID.equals("")) {
            clientSocket.sendCallError(callErrorMsgType, callID, WampException.WAMP_GENERIC_ERROR_URI, "CallID not present", null);
            return;
        }        
        
        Runnable task = new Runnable() {
            
            private boolean isCancelled(String callID) {
                Future<?> future = clientSocket.getRpcFutureResult(callID);
                return (future != null && future.isCancelled());
            }
            
            @Override
            public void run() {
                    
                String procURI = clientSocket.normalizeURI(request.get(2).asText());
                String baseURL = procURI;
                String method  = "";

                WampModule module = modules.get(baseURL);
                if(module == null) {
                    int methodPos = procURI.indexOf("#");
                    if(methodPos != -1) {
                        baseURL = procURI.substring(0, methodPos+1);
                        method = procURI.substring(methodPos+1);
                        module = getWampModule(baseURL);
                    }
                }
                
                try {
                    if(module == null) throw new Exception("ProcURI not implemented");

                    ArrayNode args = null;
                    WampCallOptions callOptions = null;
                    ObjectMapper mapper = new ObjectMapper();
                    
                    if(clientSocket.getWampVersion() > 1) {
                        args = mapper.createArrayNode();                        
                        if(request.size() > 2) {
                            if(request.get(3) instanceof ArrayNode) {
                                args = (ArrayNode)request.get(3);
                            } else {
                                args.add(request.get(3));
                            }
                        }
                        if(request.size() > 3) {
                            callOptions = new WampCallOptions((ObjectNode)request.get(4));
                        }
                    } else {
                        args = mapper.createArrayNode();
                        for(int i = 3; i < request.size(); i++) {
                            args.add(request.get(i));
                        }           
                    }

                    ArrayNode response = null;
                    if(callOptions == null) callOptions = new WampCallOptions(null);
                    Object result = module.onCall(clientSocket, method, args, callOptions);
                    if(result == null || result instanceof ArrayNode) {
                        response = (ArrayNode)result;
                    } else {
                        response = mapper.createArrayNode();
                        response.add(mapper.valueToTree(result));
                    }

                    if(!isCancelled(callID)) clientSocket.sendCallResult(callResponseMsgType, callID, response);

                } catch(Throwable ex) {

                    if(ex instanceof java.lang.reflect.InvocationTargetException) ex = ex.getCause();
                    
                    if(ex instanceof WampException) {
                        WampException wex = (WampException)ex;
                        if(!isCancelled(callID)) clientSocket.sendCallError(callErrorMsgType, callID, wex.getErrorURI(), wex.getErrorDesc(), wex.getErrorDetails());
                        logger.log(Level.FINE, "Error calling method " + method + ": " + wex.getErrorDesc());
                    } else {
                        if(!isCancelled(callID)) clientSocket.sendCallError(callErrorMsgType, callID, WampException.WAMP_GENERIC_ERROR_URI, "Error calling method " + method, ex.getMessage());
                        logger.log(Level.SEVERE, "Error calling method " + method, ex);
                    }

                } finally {
                    clientSocket.removeRpcFutureResult(callID);
                    if(isCancelled(callID)) clientSocket.sendCallError(callErrorMsgType, callID, "http://wamp.ws/err#CanceledByCaller", "RPC cancelled by caller: " + callID, null);
                }
            }
        };
        
        if(executorService == null) task.run();
        else clientSocket.addRpcFutureResult(callID, executorService.submit(task));
        
    }
    
    
    public WampTopic createTopic(String topicFQname, WampTopicOptions options)
    {
        WampTopic topic = topics.get(topicFQname);
        if(topic == null) {
            topic = new WampTopic(options);
            topic.setURI(topicFQname);
            topics.put(topicFQname, topic);
            
            for(WampTopicPattern topicPattern : topicPatterns.values()) {
                if(isTopicUriMatchingWithWildcards(topicFQname, topicPattern.getTopicUriPattern())) {
                    topicPattern.getTopics().add(topic);
                    for(WampSubscription patternSubscription : topicPattern.getSubscriptions()) {
                        try { 
                            subscribeClientWithTopic(patternSubscription.getSocket(), topic.getURI(), patternSubscription.getOptions());
                        } catch(Exception ex) {
                            logger.log(Level.FINE, "Error in subscription to topic", ex);
                        }                      
                    }
                }
            }
            
        }
        return topic;
    }
    
    
    public WampTopic removeTopic(String topicFQname)
    {
        WampTopic topic = topics.get(topicFQname);
        if(topic != null) {
            topics.remove(topicFQname);
            
            for(WampSubscription subscription : topic.getSubscriptions()) {
                try { 
                    unsubscribeClientFromTopic(subscription.getSocket(), topicFQname, subscription.getOptions());
                } catch(Exception ex) {
                    logger.log(Level.FINE, "Error in unsubscription to topic", ex);
                }                      
            }
            
            for(WampTopicPattern topicPattern : topicPatterns.values()) {
                if(isTopicUriMatchingWithWildcards(topicFQname, topicPattern.getTopicUriPattern())) {
                    topicPattern.getTopics().remove(topic);
                }
            }            
        } 
        return topic;
    }
    
    
    public boolean isTopicUriWithWildcards(String topicUrlPattern) 
    {
        int wildcardPos = topicUrlPattern.indexOf("*");
        return (wildcardPos != -1);
    }
    
    public boolean isTopicUriMatchingWithWildcards(String topicFQname, String topicUrlPattern) 
    {
        String regexp = topicUrlPattern.replace("*", ".*");
        return (topicFQname.matches(regexp));
    }

    
    public WampTopic getTopic(String topicFQname)
    {
        return topics.get(topicFQname);
    }  
    
    
    public Collection<WampTopic> getTopics(String topicUriOrPattern, WampSubscriptionOptions.MatchEnum matchType)
    {
        if(matchType == WampSubscriptionOptions.MatchEnum.prefix && !topicUriOrPattern.endsWith("*")) {
            topicUriOrPattern = topicUriOrPattern + "*";
        }
        
        WampTopicPattern topicPattern = topicPatterns.get(topicUriOrPattern);
        
        if(topicPattern != null) {
            
            return topicPattern.getTopics();
            
        } else {
        
            if(isTopicUriWithWildcards(topicUriOrPattern) && matchType != WampSubscriptionOptions.MatchEnum.exact) {
                ArrayList<WampTopic> retval = new ArrayList<WampTopic>();
                int wildcardPos = topicUriOrPattern.indexOf("*");
                String topicUriBegin = topicUriOrPattern.substring(0, wildcardPos);
                String topicUriEnd = topicUriBegin + "~";
                NavigableMap<String,WampTopic> navMap = topics.subMap(topicUriBegin, true, topicUriEnd, false);
                for(WampTopic topic : navMap.values()) {
                    if(isTopicUriMatchingWithWildcards(topic.getURI(),topicUriOrPattern)) {
                        retval.add(topic);
                    }
                }
                return retval;
            } else {                
                ArrayList<WampTopic> retval = new ArrayList<WampTopic>();
                WampTopic topic = getTopic(topicUriOrPattern);
                if(topic != null) retval.add(topic);
                return retval;
            }
        }
    }
    

    public Collection<WampTopic> subscribeClientWithTopic(WampSocket clientSocket, String topicUriOrPattern, WampSubscriptionOptions options)
    {
        // FIXME: merge subscriptions options (events & metaevents),
        // when the 1st eventhandler and 1st metahandler is subscribed
        topicUriOrPattern = clientSocket.normalizeURI(topicUriOrPattern);
        if(options == null) options = new WampSubscriptionOptions(null);
        Collection<WampTopic> topics = getTopics(topicUriOrPattern, options.getMatchType());
        
        if(isTopicUriWithWildcards(topicUriOrPattern)) {
            WampTopicPattern topicPattern = topicPatterns.get(topicUriOrPattern);
            if(topicPattern == null) {
                topicPattern = new WampTopicPattern(topicUriOrPattern, topics);
                topicPatterns.put(topicUriOrPattern, topicPattern);
            }
            
            WampSubscription subscription = topicPattern.getSubscription(clientSocket.getSessionId());
            if(subscription == null) subscription = new WampSubscription(clientSocket, topicUriOrPattern, options);
            if(subscription.refCount(+1) == 1) topicPattern.addSubscription(subscription);
            //clientSocket.addSubscription(subscription);
        } 
        
        for(WampTopic topic : topics) {
            WampModule module = getWampModule(topic.getBaseURI());
            try { 
                module.onSubscribe(clientSocket, topic, options);
            } catch(Exception ex) {
                if(options != null && options.hasMetaEventsEnabled()) {
                    try { 
                        ObjectNode metaevent = (new ObjectMapper()).createObjectNode();
                        metaevent.put("error", ex.getMessage());
                        publishMetaEvent(topic, WampMetaTopic.DENIED, metaevent, clientSocket);
                        logger.log(Level.FINE, "Error in subscription to topic", ex);
                    } catch(Exception ex2) { }
                }
            }
        }
    
        return topics;
    }
    
    
    public Collection<WampTopic> unsubscribeClientFromTopic(WampSocket clientSocket, String topicUriOrPattern, WampSubscriptionOptions options)
    {
        topicUriOrPattern = clientSocket.normalizeURI(topicUriOrPattern);
        if(options == null) options = new WampSubscriptionOptions(null);
        
        Collection<WampTopic> topics = getTopics(topicUriOrPattern, options.getMatchType());
        if(isTopicUriWithWildcards(topicUriOrPattern)) {
            WampTopicPattern topicPattern = topicPatterns.get(topicUriOrPattern);
            WampSubscription subscription = topicPattern.getSubscription(clientSocket.getSessionId());
            if(subscription.refCount(-1) <= 0) clientSocket.removeSubscription(topicUriOrPattern);
            /** Don't clear topicPatterns for future clients
            // topicPatterns.remove(topicUriOrPattern);
            */
        }
        
        for(WampTopic topic : topics) {
            WampSubscription subscription = topic.getSubscription(clientSocket.getSessionId());
            if(subscription != null) {
                try { 
                    WampModule module = getWampModule(topic.getBaseURI());
                    module.onUnsubscribe(clientSocket, topic);
                } catch(Exception ex) {
                    logger.log(Level.FINE, "Error in unsubscription to topic", ex);
                }          
            }
        }
        
        return topics;
    }
    
    
    private void processPublishMessage(WampSocket clientSocket, ArrayNode request) throws Exception 
    {
        String topicName = clientSocket.normalizeURI(request.get(1).asText());
        WampTopic topic = getTopic(topicName);
        try {
            WampModule module = getWampModule(topic.getBaseURI());
            module.onPublish(clientSocket, topic, request);
        } catch(Exception ex) {
            logger.log(Level.FINE, "Error in publishing to topic", ex);
        }  
    }
    
    
    public void publishEvent(String publisherId, WampTopic topic, JsonNode event, WampPublishOptions options) 
    {
        logger.log(Level.INFO, "Broadcasting to {0}: {1}", new Object[]{topic.getURI(),event});
        try {
            WampModule module = getWampModule(topic.getBaseURI());
            module.onEvent(publisherId, topic, event, options);
        } catch(Exception ex) {
            logger.log(Level.SEVERE, "Error in publishing event to topic", ex);
        }
    }   
    
    public void publishMetaEvent(WampTopic topic, String metatopic, JsonNode metaevent, WampSocket toClient) 
    {
        logger.log(Level.INFO, "Broadcasting to {0}: {1}", new Object[]{topic.getURI(),metaevent});
        try {
            WampModule module = getWampModule(topic.getBaseURI());
            module.onMetaEvent(topic, metatopic, metaevent, toClient);
        } catch(Exception ex) {
            logger.log(Level.SEVERE, "Error in publishing metaevent to topic", ex);
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
