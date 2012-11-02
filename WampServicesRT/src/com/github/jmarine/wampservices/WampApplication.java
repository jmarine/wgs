/**
 * WebSocket Message Application Protocol implementation
 *
 * @author Jordi Marine Fort 
 */

package com.github.jmarine.wampservices;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.websockets.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;



public class WampApplication extends WebSocketApplication 
{
    private static final Logger logger = Logger.getLogger(WampApplication.class.getName());

    public  static final String WAMP_BASE_URL = "https://github.com/jmarine/wampservices";
    
    private String contextPath;
    private Map<String,WampModule> modules;
    private TreeMap<String,WampTopic> topics;
    private TreeMap<String,WampTopicPattern> topicPatterns;
    private WampModule defaultModule;
    private boolean topicWildcardsEnabled;
    private Properties wampConfig;

    
    public WampApplication(Properties wampConfig, String contextPath, boolean topicWildcardsEnabled) 
    {
        this.wampConfig = wampConfig;
        this.contextPath = contextPath;
        this.modules = new HashMap<String,WampModule>();
        this.topics = new TreeMap<String,WampTopic>();
        this.topicPatterns = new TreeMap<String,WampTopicPattern>();
        this.topicWildcardsEnabled = topicWildcardsEnabled;
        
        this.defaultModule = new WampModule(this) {
            @Override
            public String getBaseURL() {
                return WAMP_BASE_URL;
            }
        };
    }
    
    public Properties getWampConfig()
    {
        return this.wampConfig;
    }

    /**
     * Creates a customized {@link WebSocket} implementation.
     * 
     * @return customized {@link WebSocket} implementation - {@link WampClient}
     */
    @Override
    public WebSocket createWebSocket(ProtocolHandler handler,
                                  //HttpRequestPacket request,
                                  WebSocketListener... listeners) 
    {
        WampSocket socket = new WampSocket(this, handler, listeners);
        return socket;
    }

    @Override
    public boolean isApplicationRequest(Request request) 
    {
        return contextPath.equals(request.requestURI().toString());
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
    

    /**
     * Invoked when the opening handshake has been completed for a specific
     * {@link WebSocket} instance.
     * 
     * @param socket the newly connected {@link WebSocket}
     */
    @Override
    public void onConnect(WebSocket websocket)
    {
        super.onConnect(websocket);
        WampSocket clientSocket = (WampSocket)websocket;
        for(WampModule module : modules.values()) {
            try { 
                module.onConnect(clientSocket); 
            } catch(Exception ex) {
                logger.log(Level.SEVERE, "Error disconnecting socket:", ex);
            }
        }        
        clientSocket.sendSafe("[0,\"" + clientSocket.getSessionId() + "\", 1, \"" + getServerId() +"\" ]"); 
    }


    /**
     * Method is called, when {@link WampSocket} receives a {@link Frame}.
     * @param websocket {@link WampSocket}
     * @param data {@link Frame}
     *
     * @throws IOException
     */
    @Override
    public void onMessage(WebSocket websocket, String data) 
    {
        try {
            logger.log(Level.FINE, "onMessage.data = {0}", new Object[]{data});

	    WampSocket  clientSocket = (WampSocket)websocket;
            ObjectMapper mapper = new ObjectMapper();
            ArrayNode    request = (ArrayNode)mapper.readTree(data);

            int requestType = request.get(0).asInt();
            logger.log(Level.INFO, "Request type = {0}", new Object[]{requestType});

	    try {
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

            } catch(Exception ex) {
                logger.log(Level.SEVERE, "Error processing request", ex);
            }



        } catch(Exception ex) {
           logger.log(Level.SEVERE, "Invalid WAMP request", ex);
        }
        
    }
    

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClose(WebSocket websocket, DataFrame frame) 
    {
        super.onClose(websocket, frame);        
        websocket.setClosed();
        
        WampSocket clientSocket = (WampSocket)websocket;
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
                            WampModule module = getWampModule(topic.getBaseURI());
                            module.onSubscribe(patternSubscription.getSocket(), topic, patternSubscription.getOptions());
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
            
            WampModule module = getWampModule(topic.getBaseURI());
            for(WampSubscription subscription : topic.getSubscriptions()) {
                try { 
                    module.onUnsubscribe(subscription.getSocket(), topic);
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
        return (topicWildcardsEnabled) && (wildcardPos != -1);
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
            try { 
                WampModule module = getWampModule(topic.getBaseURI());
                module.onSubscribe(clientSocket, topic, options);
            } catch(Exception ex) {
                logger.log(Level.FINE, "Error in subscription to topic", ex);
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
                case '<':
                    buffer.append("&lt;");
                    break;
                case '>':
                    buffer.append("&gt;");
                    break;
                case '&':
                    buffer.append("&amp;");
                    break;
                default:
                    buffer.append(c);
            }
        }
        buffer.append("\"");
        return buffer.toString();
    }


}
