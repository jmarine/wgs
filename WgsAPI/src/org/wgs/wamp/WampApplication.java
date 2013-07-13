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
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
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
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;


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
    private TreeMap<String,WampTopic> topics;
    private TreeMap<String,WampTopicPattern> topicPatterns;
    private ConcurrentHashMap<Session,WampSocket> sockets;
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
        
        this.sockets = new ConcurrentHashMap<Session,WampSocket>();
        this.modules = new HashMap<String,WampModule>();
        this.topics = new TreeMap<String,WampTopic>();
        this.topicPatterns = new TreeMap<String,WampTopicPattern>();
        
        this.registerWampModule(WampAPI.class);
        
        this.defaultModule = new WampModule(this);
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
    
    private WampSocket getWampSocket(Session session)
    {
        return sockets.get(session);
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
        switch(wampVersion) {
            case WampApplication.WAMPv1:
                response.add(1);  // WAMP v1
                response.add(getServerId());
                break;
            default:
                response.add(helloDetails);  // WAMP v2
                break;
        }
        clientSocket.sendWampResponse(response);
        
    }   

    public void onWampMessage(WampSocket clientSocket, ArrayNode request) throws Exception
    {

        logger.log(Level.FINE, "onWampMessage.data = {0}", new Object[]{request});

        int requestType = request.get(0).asInt();
        logger.log(Level.INFO, "Request type = {0}", new Object[]{requestType});


        switch(requestType) {
            case 0:
                clientSocket.setVersionSupport(WampApplication.WAMPv2);
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
                if(subscription.getOptions().getMatchType() != WampSubscriptionOptions.MatchEnum.exact) {  // prefix or wildcards
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
    
    
    public WampTopic createTopic(String topicFQname, WampTopicOptions options)
    {
        WampTopic topic = topics.get(topicFQname);
        if(topic == null) {
            topic = new WampTopic(options);
            topic.setURI(topicFQname);
            topics.put(topicFQname, topic);
            
            for(WampTopicPattern topicPattern : topicPatterns.values()) {
                if(isTopicUriMatchingWithWildcards(topicFQname, topicPattern.getTopicUriPattern(), topicPattern.getMatchType())) {
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
                if(isTopicUriMatchingWithWildcards(topicFQname, topicPattern.getTopicUriPattern(), topicPattern.getMatchType())) {
                    topicPattern.getTopics().remove(topic);
                }
            }            
        } 
        return topic;
    }
    

    public boolean isTopicUriMatchingWithWildcards(String topicFQname, String topicUrlPattern, WampSubscriptionOptions.MatchEnum matchType) 
    {
        String regexp = (matchType==WampSubscriptionOptions.MatchEnum.prefix)? topicUrlPattern.replace("*", ".*") : topicUrlPattern.replace("*", ".+");
        return (topicFQname.matches(regexp));
    }

    
    public WampTopic getTopic(String topicFQname)
    {
        return topics.get(topicFQname);
    }  
    
    
    public Collection<WampTopic> getTopics(WampSubscriptionOptions.MatchEnum matchType, String topicUriOrPattern)
    {
        String topicPatternKey = matchType.toString() + ">" + topicUriOrPattern;
        WampTopicPattern topicPattern = topicPatterns.get(topicPatternKey);
        
        if(topicPattern != null) {
            
            return topicPattern.getTopics();
            
        } else {
        
            if(matchType != WampSubscriptionOptions.MatchEnum.exact) {  // prefix or wildcards
                ArrayList<WampTopic> retval = new ArrayList<WampTopic>();
                int wildcardPos = topicUriOrPattern.indexOf("*");
                String topicUriBegin = topicUriOrPattern.substring(0, wildcardPos);
                String topicUriEnd = topicUriBegin + "~";
                NavigableMap<String,WampTopic> navMap = topics.subMap(topicUriBegin, true, topicUriEnd, false);
                for(WampTopic topic : navMap.values()) {
                    if(isTopicUriMatchingWithWildcards(topic.getURI(), topicUriOrPattern, matchType)) {
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
        if(options.getMatchType() == WampSubscriptionOptions.MatchEnum.prefix && !topicUriOrPattern.endsWith("*")) {
            topicUriOrPattern = topicUriOrPattern + "*";
        }
        
        Collection<WampTopic> topics = getTopics(options.getMatchType(), topicUriOrPattern);
        
        if(options.getMatchType() != WampSubscriptionOptions.MatchEnum.exact) {  // prefix or wildcards
            String topicPatternKey = options.getMatchType().toString() + ">" + topicUriOrPattern;
            WampTopicPattern topicPattern = topicPatterns.get(topicPatternKey);
            if(topicPattern == null) {
                topicPattern = new WampTopicPattern(options.getMatchType(), topicUriOrPattern, topics);
                topicPatterns.put(topicPatternKey, topicPattern);
            }
            
            WampSubscription subscription = topicPattern.getSubscription(clientSocket.getSessionId());
            if(subscription == null) subscription = new WampSubscription(clientSocket, topicUriOrPattern, options);
            if(subscription.refCount(+1) == 1) topicPattern.addSubscription(subscription);
            //clientSocket.addSubscription(subscription);
        } 
        
        for(WampTopic topic : topics) {
            WampModule module = getWampModule(topic.getBaseURI(), getDefaultWampModule());
            try { 
                module.onSubscribe(clientSocket, topic, options);
            } catch(Exception ex) {
                if(options != null && options.hasMetaEvents()) {
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
        if(options.getMatchType() == WampSubscriptionOptions.MatchEnum.prefix && !topicUriOrPattern.endsWith("*")) {
            topicUriOrPattern = topicUriOrPattern + "*";
        }
        
        Collection<WampTopic> topics = getTopics(options.getMatchType(), topicUriOrPattern);
        if(options.getMatchType() != WampSubscriptionOptions.MatchEnum.exact) {  // prefix or wildcard
            String topicPatternKey = options.getMatchType().toString() + ">" + topicUriOrPattern;
            WampTopicPattern topicPattern = topicPatterns.get(topicPatternKey);
            WampSubscription subscription = topicPattern.getSubscription(clientSocket.getSessionId());
            if(subscription.refCount(-1) <= 0) clientSocket.removeSubscription(topicUriOrPattern);
            /** Don't clear topicPatterns for future clients
            // topicPatterns.remove(topicPatternKey);
            */
        }
        
        for(WampTopic topic : topics) {
            WampSubscription subscription = topic.getSubscription(clientSocket.getSessionId());
            if(subscription != null) {
                try { 
                    WampModule module = getWampModule(topic.getBaseURI(), getDefaultWampModule());
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
            WampModule module = getWampModule(topic.getBaseURI(), getDefaultWampModule());
            module.onPublish(clientSocket, topic, request);
        } catch(Exception ex) {
            logger.log(Level.FINE, "Error in publishing to topic", ex);
        }  
    }
    
    
    public void publishEvent(String publisherId, WampTopic topic, JsonNode event, WampPublishOptions options) 
    {
        logger.log(Level.INFO, "Broadcasting to {0}: {1}", new Object[]{topic.getURI(),event});
        try {
            WampModule module = getWampModule(topic.getBaseURI(), getDefaultWampModule());
            module.onEvent(publisherId, topic, event, options);
        } catch(Exception ex) {
            logger.log(Level.SEVERE, "Error in publishing event to topic", ex);
        }
    }   
    
    public void publishMetaEvent(WampTopic topic, String metatopic, JsonNode metaevent, WampSocket toClient) 
    {
        logger.log(Level.INFO, "Broadcasting to {0}: {1}", new Object[]{topic.getURI(),metaevent});
        try {
            WampModule module = getWampModule(topic.getBaseURI(), getDefaultWampModule());
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
