/**
 * WebSocket Message Application Protocol implementation
 *
 * @author Jordi Marine Fort 
 */

package com.github.jmarine.wampservices;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.websocket.CloseReason;
import javax.net.websocket.Endpoint;
import javax.net.websocket.MessageHandler;
import javax.net.websocket.Session;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;


public class WampApplication extends Endpoint 
{
    private static final Logger logger = Logger.getLogger(WampApplication.class.getName());

    public  static final String WAMP_BASE_URL = "https://github.com/jmarine/wampservices";
    
    private String contextPath;
    private Map<String,WampModule> modules;
    private TreeMap<String,WampTopic> topics;
    private TreeMap<String,WampTopicPattern> topicPatterns;
    private WampModule defaultModule;
    private Properties wampConfig;
    private ConcurrentHashMap<Session,WampSocket> sockets;
    

    
    public WampApplication() 
    {
        this.sockets = new ConcurrentHashMap<Session,WampSocket>();
        this.modules = new HashMap<String,WampModule>();
        this.topics = new TreeMap<String,WampTopic>();
        this.topicPatterns = new TreeMap<String,WampTopicPattern>();
        
        this.defaultModule = new WampModule(this) {
            @Override
            public String getBaseURL() {
                return WAMP_BASE_URL;
            }
        };
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
    

    @Override
    public void onOpen(final Session session) {
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


        session.addMessageHandler(new MessageHandler.Text() {

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
        response.add(0);  // WELCOME message code
        response.add(clientSocket.getSessionId());
        response.add(1);  // WAMP version
        response.add(getServerId());
        clientSocket.sendWampResponse(response);
        
    }


    public void onWampMessage(WampSocket clientSocket, ArrayNode request) throws Exception
    {

        logger.log(Level.FINE, "onWampMessage.data = {0}", new Object[]{request});

        int requestType = request.get(0).asInt();
        logger.log(Level.INFO, "Request type = {0}", new Object[]{requestType});


        switch(requestType) {
            case 1:
                processPrefixMessage(clientSocket, request);
                break;
            case 2:
                processCallMessage(clientSocket, request);
                break;
            case 5:
                JsonNode jsonOptionsNode = (request.size() > 2) ? request.get(2) : null;
                WampSubscriptionOptions options = new WampSubscriptionOptions(jsonOptionsNode);
                String subscriptionTopicName = request.get(1).asText();
                subscribeClientWithTopic(clientSocket, subscriptionTopicName, options);
                break;
            case 6:
                String unsubscriptionTopicName = request.get(1).asText();
                unsubscribeClientFromTopic(clientSocket, unsubscriptionTopicName);
                break;
            case 7:
                processPublishMessage(clientSocket, request);
                break;                
            default:
                logger.log(Level.SEVERE, "Request type not implemented: {0}", new Object[]{requestType});
        }


    }
    

    

    
    @Override
    public void onError(Throwable thr, Session session) 
    {
         super.onError(thr, session);        
         System.out.println("##################### Session error");
         onClose(session, new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "onError"));
    }    


    @Override
    public void onClose(Session session, CloseReason reason) 
    {
        super.onClose(session, reason);
    
        try { session.close(reason); }
        catch(Exception ex) { }
        
        WampSocket clientSocket = sockets.remove(session);
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
                unsubscribeClientFromTopic(clientSocket, subscription.getTopicUriOrPattern());
            }
        }
        
        // Then, remove remaining subscriptions to single topics:
        for(WampSubscription subscription : clientSocket.getSubscriptions()) {
            unsubscribeClientFromTopic(clientSocket, subscription.getTopicUriOrPattern());
        }        

        logger.log(Level.INFO, "Socket disconnected: {0}", new Object[] {clientSocket.getSessionId()});
    }
    
    
    public void registerWampModule(Class moduleClass) throws Exception
    {
        WampModule module = (WampModule)moduleClass.getConstructor(WampApplication.class).newInstance(this);
        modules.put(module.getBaseURL(), module);
    }

    
    private void processPrefixMessage(WampSocket clientSocket, ArrayNode request) throws Exception
    {
        String prefix = request.get(1).asText();
        String url = request.get(2).asText();
	clientSocket.registerPrefixURL(prefix, url);
    }
    
   
    private void processCallMessage(WampSocket clientSocket, ArrayNode request) throws Exception
    {
        String callID  = request.get(1).asText();
        if(callID == null || callID.equals("")) {
            clientSocket.sendCallError(callID, WampException.WAMP_GENERIC_ERROR_URI, "CallID not present", null);
            return;
        }
        
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
            
            ObjectMapper mapper = new ObjectMapper();
            ArrayNode args = mapper.createArrayNode();
            for(int i = 3; i < request.size(); i++) {
                args.add(request.get(i));
            }           
            
            synchronized(clientSocket) {
                ArrayNode response = null;
                Object result = module.onCall(clientSocket, method, args);
                if(result == null || result instanceof ArrayNode) {
                    response = (ArrayNode)result;
                } else {
                    response = mapper.createArrayNode();
                    response.add(mapper.valueToTree(result));
                }
                clientSocket.sendCallResult(callID, response);
            }
            
        } catch(Throwable ex) {
            
            if(ex instanceof java.lang.reflect.InvocationTargetException) ex = ex.getCause();
            
            if(ex instanceof WampException) {
                WampException wex = (WampException)ex;
                clientSocket.sendCallError(callID, wex.getErrorURI(), wex.getErrorDesc(), wex.getErrorDetails());
                logger.log(Level.FINE, "Error calling method " + method + ": " + wex.getErrorDesc());
            } else {
                clientSocket.sendCallError(callID, WampException.WAMP_GENERIC_ERROR_URI, "Error calling method " + method, ex.getMessage());
                logger.log(Level.SEVERE, "Error calling method " + method, ex);
            }
            
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
                String regexp = topicPattern.getTopicUriPattern().replace("*", ".*");
                if(topicFQname.matches(regexp)) {
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
                    unsubscribeClientFromTopic(subscription.getSocket(), topicFQname);
                } catch(Exception ex) {
                    logger.log(Level.FINE, "Error in unsubscription to topic", ex);
                }                      
            }
            
            for(WampTopicPattern topicPattern : topicPatterns.values()) {
                String regexp = topicPattern.getTopicUriPattern().replace("*", ".*");
                if(topicFQname.matches(regexp)) {
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

    
    public WampTopic getTopic(String topicFQname)
    {
        return topics.get(topicFQname);
    }  
    
    
    public Collection<WampTopic> getTopics(String topicUriOrPattern)
    {
        WampTopicPattern topicPattern = topicPatterns.get(topicUriOrPattern);
        
        if(topicPattern != null) {
            
            return topicPattern.getTopics();
            
        } else {
        
            if(isTopicUriWithWildcards(topicUriOrPattern)) {
                int wildcardPos = topicUriOrPattern.indexOf("*");
                String topicUriBegin = topicUriOrPattern.substring(0, wildcardPos);
                String topicUriEnd = topicUriBegin + "~";
                NavigableMap<String,WampTopic> navMap = topics.subMap(topicUriBegin, true, topicUriEnd, false);
                return navMap.values();
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
        Collection<WampTopic> topics = getTopics(topicUriOrPattern);
        
        if(isTopicUriWithWildcards(topicUriOrPattern)) {
            WampSubscription subscription = new WampSubscription(clientSocket, topicUriOrPattern, options);
            clientSocket.addSubscription(subscription);

            WampTopicPattern topicPattern = topicPatterns.get(topicUriOrPattern);
            if(topicPattern == null) {
                topicPattern = new WampTopicPattern(topicUriOrPattern, topics);
                topicPatterns.put(topicUriOrPattern, topicPattern);
            }
        } 
        
        for(WampTopic topic : topics) {
            String metatopic = null;
            ObjectNode metaevent = null;
            WampModule module = getWampModule(topic.getBaseURI());
            try { 
                module.onSubscribe(clientSocket, topic, options);
                metatopic = "http://wamp.ws/sub#ok";
            } catch(Exception ex) {
                metatopic = "http://wamp.ws/sub#denied";
                metaevent = (new ObjectMapper()).createObjectNode();
                metaevent.put("error", ex.getMessage());
                logger.log(Level.FINE, "Error in subscription to topic", ex);
            }          

            if(options != null && options.isMetaEventsEnabled()) {
                try { 
                    publishMetaEvent(topic, metatopic, metaevent, clientSocket);
                } catch(Exception ex) { }
            }
        }
    
        return topics;
    }
    
    
    public Collection<WampTopic> unsubscribeClientFromTopic(WampSocket clientSocket, String topicUriOrPattern)
    {
        topicUriOrPattern = clientSocket.normalizeURI(topicUriOrPattern);
        Collection<WampTopic> topics = getTopics(topicUriOrPattern);
        if(isTopicUriWithWildcards(topicUriOrPattern)) {
            clientSocket.removeSubscription(topicUriOrPattern);
            /** Don't clear topicPatterns for future clients
            WampTopicPattern topicPattern = topicPatterns.get(topicUriOrPattern);
            if(topicPattern.getSubscriptions().size() == 0) {
                topicPatterns.remove(topicUriOrPattern);
            }
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
    
    
    public void publishEvent(String publisherId, WampTopic topic, JsonNode event, Set<String> excluded, Set<String> eligible) 
    {
        logger.log(Level.INFO, "Broadcasting to {0}: {1}", new Object[]{topic.getURI(),event});
        if(eligible == null) eligible = topic.getSessionIds();
        else eligible.retainAll(topic.getSessionIds());
        try {
            WampModule module = getWampModule(topic.getBaseURI());
            module.onEvent(publisherId, topic, event, excluded, eligible);
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


}
