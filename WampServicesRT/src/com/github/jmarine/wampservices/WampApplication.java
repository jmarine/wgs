package com.github.jmarine.wampservices;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.websockets.*;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.TextNode;



/**
 * WebSocket Message Application Protocol implementation
 *
 * @author Jordi Marine Fort 
 */
public class WampApplication extends WebSocketApplication {
    private static final Logger logger = Logger.getLogger(WampApplication.class.getName());

    public  static final String WAMP_BASE_URL = "https://github.com/jmarine/wampservices";
    
    private String contextPath;
    private Map<String,WampTopic>  topics;        
    private Map<String,WampModule> modules;
    private WampModule defaultModule;
    

    
    public WampApplication(String contextPath) {
        this.contextPath = contextPath;
        this.modules = new HashMap<String,WampModule>();
        this.topics = new ConcurrentHashMap<String,WampTopic>();
        
        this.defaultModule = new WampModule(this) {
            @Override
            public String getBaseURL() {
                return WAMP_BASE_URL;
            }
        };
    }

    /**
     * Creates a customized {@link WebSocket} implementation.
     * 
     * @return customized {@link WebSocket} implementation - {@link WampClient}
     */
    @Override
    public WebSocket createWebSocket(ProtocolHandler handler,
                                  //HttpRequestPacket request,
                                  WebSocketListener... listeners) {
        WampSocket socket = new WampSocket(this, handler, listeners);
        return socket;
    }

    @Override
    public boolean isApplicationRequest(Request request) {
        return contextPath.equals(request.requestURI().toString());
    }

    
    public WampModule getWampModule(String moduleBaseURI) 
    {
        WampModule module = modules.get(moduleBaseURI);
        if(module == null) module = defaultModule;
        return module;
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
        clientSocket.sendSafe("[0,\"" + clientSocket.getSessionId() + "\", 1, \"wsAppManager\" ]"); 
    }


    /**
     * Method is called, when {@link WampSocket} receives a {@link Frame}.
     * @param websocket {@link WampSocket}
     * @param data {@link Frame}
     *
     * @throws IOException
     */
    @Override
    public void onMessage(WebSocket websocket, String data) {

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
                    String subscriptionTopicName = request.get(1).asText();
		    subscribeClientWithTopic(clientSocket, subscriptionTopicName);
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
    public void onClose(WebSocket websocket, DataFrame frame) {
        WampSocket clientSocket = (WampSocket)websocket;
        for(WampModule module : modules.values()) {
            try { 
                module.onDisconnect(clientSocket); 
            } catch(Exception ex) {
                logger.log(Level.SEVERE, "Error disconnecting client:", ex);
            }
        }
        for(String topicName : clientSocket.getTopics().keySet()) {
            unsubscribeClientFromTopic(clientSocket, topicName);
        }
        super.onClose(websocket, frame);
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
            
            ArrayNode response = null;
            Object result = module.onCall(clientSocket, method, args);
            if(result == null || result instanceof ArrayNode) {
                response = (ArrayNode)result;
            } else {
                response = mapper.createArrayNode();
                response.add(mapper.valueToTree(result));
            }
            clientSocket.sendCallResult(callID, response);

        } catch(WampException ex) {
            clientSocket.sendCallError(callID, ex.getErrorURI(), ex.getErrorDesc(), ex.getErrorDetails());
            logger.log(Level.SEVERE, "Error calling method " + method, ex);
            
        } catch(Exception ex) {
            clientSocket.sendCallError(callID, WampException.WAMP_GENERIC_ERROR_URI, "Error calling method " + method, new Object[] { ex.getMessage() });
            logger.log(Level.SEVERE, "Error calling method " + method, ex);
        }
    }
    
     
    public WampTopic getTopic(String topicFQname)
    {
        return topics.get(topicFQname);
    }
    
    public WampTopic createTopic(String topicFQname)
    {
        WampTopic topic = topics.get(topicFQname);
        if(topic == null) {
            topic = new WampTopic();
            topic.setURI(topicFQname);
            topics.put(topicFQname, topic);
        }
        return topic;
    }
    
    
    public WampTopic deleteTopic(String topicFQname)
    {
        WampTopic topic = topics.get(topicFQname);
        if(topic != null) {
            topics.remove(topicFQname);
        } 
        return topic;
    }
    

    public WampTopic subscribeClientWithTopic(WampSocket clientSocket, String topicName)
    {
        topicName = clientSocket.normalizeURI(topicName);
        WampTopic topic = getTopic(topicName);
        
        if(topic != null) {
            try { 
                WampModule module = getWampModule(topic.getBaseURI());
                module.onSubscribe(clientSocket, topic); 
            } catch(Exception ex) {
                logger.log(Level.SEVERE, "Error in subscription to topic", ex);
            }          
        }
        
        return topic;
    }
    
    public WampTopic unsubscribeClientFromTopic(WampSocket clientSocket, String topicName)
    {
        topicName = clientSocket.normalizeURI(topicName);
        WampTopic topic = getTopic(topicName);
        if(topic != null) {
            try { 
                WampModule module = getWampModule(topic.getBaseURI());
                module.onUnsubscribe(clientSocket, topic); 
            } catch(Exception ex) {
                logger.log(Level.SEVERE, "Error in unsubscription to topic", ex);
            }          
            
        }
        return topic;
    }
    
    
    private void processPublishMessage(WampSocket clientSocket, ArrayNode request) throws Exception 
    {
        String topicName = clientSocket.normalizeURI(request.get(1).asText());
        WampTopic topic = getTopic(topicName);
        JsonNode event = request.get(2);
        if(request.size() == 3) {
            clientSocket.publishEvent(topic, event, false);
        } else if(request.size() == 4) {
            // Argument 4 could be a BOOLEAN(excludeMe) or JSONArray(excludedIds)
            try {
                boolean excludeMe = request.get(3).asBoolean();
                clientSocket.publishEvent(topic, event, excludeMe);
            } catch(Exception ex) {
                HashSet<String> excludedSet = new HashSet<String>();
                ArrayNode excludedArray = (ArrayNode)request.get(3);
                for(int i = 0; i < excludedArray.size(); i++) {
                    excludedSet.add(excludedArray.get(i).asText());
                }
                clientSocket.publishEvent(topic, event, excludedSet, null);
            }
        } else if(request.size() == 5) {
            HashSet<String> excludedSet = new HashSet<String>();
            HashSet<String> eligibleSet = new HashSet<String>();
            ArrayNode excludedArray = (ArrayNode)request.get(3);
            for(int i = 0; i < excludedArray.size(); i++) {
                excludedSet.add(excludedArray.get(i).asText());
            }
            ArrayNode eligibleArray = (ArrayNode)request.get(4);
            for(int i = 0; i < eligibleArray.size(); i++) {
                eligibleSet.add(eligibleArray.get(i).asText());
            }
            clientSocket.publishEvent(topic, event, excludedSet, eligibleSet);
        }
        
    }
    
    
    public String encodeJSON(String orig) {
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
