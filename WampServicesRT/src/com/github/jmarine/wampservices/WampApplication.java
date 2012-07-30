package com.github.jmarine.wampservices;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.websockets.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

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
    

    
    public WampApplication(String contextPath) {
        this.contextPath = contextPath;
        this.modules = new HashMap<String,WampModule>();
        this.topics = new ConcurrentHashMap<String,WampTopic>();
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
        WampSocket socket = new WampSocket(handler, listeners);
        return socket;
    }

    @Override
    public boolean isApplicationRequest(Request request) {
        return contextPath.equals(request.requestURI().toString());
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
            JSONTokener tokener = new JSONTokener(data);
            JSONArray   request = new JSONArray(tokener);

            int requestType = request.getInt(0);
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
                    String subscriptionTopicName = request.getString(1);
		    subscribeClientWithTopic(clientSocket, subscriptionTopicName);
                    break;
		case 6:
                    String unsubscriptionTopicName = request.getString(1);
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

    
    private void processPrefixMessage(WampSocket clientSocket, JSONArray request) throws Exception
    {
        String prefix = request.getString(1);
        String url = request.getString(2);
	clientSocket.registerPrefixURL(prefix, url);
    }
    
    private void processPublishMessage(WampSocket clientSocket, JSONArray request) throws Exception 
    {
        String publicationTopicName = request.getString(1);
        JSONObject event = request.getJSONObject(2);
        if(request.length() == 3) {
            publishEvent(clientSocket, publicationTopicName, event, false);
        } else if(request.length() == 4) {
            // Argument 4 could be a BOOLEAN(excludeMe) or JSONArray(excludedIds)
            try {
                boolean excludeMe = request.getBoolean(3);
                publishEvent(clientSocket, publicationTopicName, event, excludeMe);
            } catch(Exception ex) {
                HashSet<String> excludedSet = new HashSet<String>();
                JSONArray excludedArray = request.getJSONArray(3);
                for(int i = 0; i < excludedArray.length(); i++) {
                    excludedSet.add(excludedArray.getString(i));
                }
                publishEvent(clientSocket, publicationTopicName, event, excludedSet, null);
            }
        } else if(request.length() == 5) {
            HashSet<String> excludedSet = new HashSet<String>();
            HashSet<String> eligibleSet = new HashSet<String>();
            JSONArray excludedArray = request.getJSONArray(3);
            for(int i = 0; i < excludedArray.length(); i++) {
                excludedSet.add(excludedArray.getString(i));
            }
            JSONArray eligibleArray = request.getJSONArray(4);
            for(int i = 0; i < eligibleArray.length(); i++) {
                eligibleSet.add(eligibleArray.getString(i));
            }
            publishEvent(clientSocket, publicationTopicName, event, excludedSet, eligibleSet);
        }
        
    }
    
   
    private String normalizeURI(WampSocket clientSocket, String curie) {
        int curiePos = curie.indexOf(":");
        if(curiePos != -1) {
            String prefix = curie.substring(0, curiePos);
            String baseURI = (String)clientSocket.getPrefixURL(prefix);
            if(baseURI != null) curie = baseURI + curie.substring(curiePos+1);
        }
        return curie;
    }

    
    public JSONArray createWampErrorArg(String errorURI, String errorDesc) throws Exception {
        JSONObject error = new JSONObject();
        if(errorURI == null) errorURI = WAMP_BASE_URL + "#error";
        if(errorDesc == null) errorDesc = "";
        error.put("errorURI", errorURI);
        error.put("errorDesc", errorDesc);
        JSONArray retval = new JSONArray();
        retval.put(error);
        return retval;
    }

    private void processCallMessage(WampSocket clientSocket, JSONArray request) throws Exception
    {
        String callID  = request.getString(1);
        if(callID == null || callID.equals("")) {
            sendCallResponse(false, callID, createWampErrorArg(null, "CallID not present"), clientSocket);
            return;
        }
        
        String procURI = normalizeURI(clientSocket, request.getString(2));
        String baseURL = procURI;
        String method  = "";
        
        WampModule module = modules.get(baseURL);
        if(module == null) {
            int methodPos = procURI.indexOf("#");
            if(methodPos != -1) {
                baseURL = procURI.substring(0, methodPos+1);
                method = procURI.substring(methodPos+1);
                module = modules.get(baseURL);
            }
            if(module == null) throw new Exception("ProcURI not implemented");
        }
        
        JSONArray args = new JSONArray();
        for(int i = 3; i < request.length(); i++) {
            args.put(i-3, request.get(i));
        }

        try {
            JSONArray response = null;
            Object result = module.onCall(clientSocket, method, args);
            if(result == null || result instanceof JSONArray) {
                response = (JSONArray)result;
            } else {
                response = new JSONArray();
                response.put(result);
            }
            sendCallResponse(true, callID, response, clientSocket);
        } catch(Exception ex) {
            sendCallResponse(false, callID, createWampErrorArg(null, "Error calling method " + method + ": " + ex.getMessage()), clientSocket);
            logger.log(Level.SEVERE, "Error calling method " + method, ex);
        }
    }
    

    public void sendCallResponse(boolean valid, String callID, JSONArray args, WampSocket clientSocket)
    {
        StringBuilder response = new StringBuilder();
        if(args == null) {
            args = new JSONArray();
            args.put((String)null);
        }

        response.append("[");
        if(valid) response.append("3");
        else response.append("4");
        response.append(",");
        response.append(encodeJSON(callID));
        if(!valid) {
            String errorURI = WAMP_BASE_URL + "#error";
            try { 
                errorURI = ((JSONObject)args.get(0)).getString("errorURI"); 
                ((JSONObject)args.get(0)).remove("errorURI");
            } catch(Exception ex) { }
            
            String errorDesc = "";
            try { 
                errorDesc = ((JSONObject)args.get(0)).getString("errorDesc"); 
                ((JSONObject)args.get(0)).remove("errorDesc");
            } catch(Exception ex) { }
            
            try {
                if(((JSONObject)args.get(0)).length() == 0) {
                   args.remove(0);
                }
            } catch(Exception ex) { }

            response.append(",");
            response.append(encodeJSON(errorURI));
            response.append(",");
            response.append(encodeJSON(errorDesc));
        }
        for(int i = 0; i < args.length(); i++) {
            response.append(",");
            try { response.append(args.get(i)); }
            catch(Exception ex) { response.append("null"); }
        }
        response.append("]");
        clientSocket.sendSafe(response.toString());
    }

    
    public WampTopic getTopic(WampSocket clientSocket, String topicName)
    {
        String topicFQname = normalizeURI(clientSocket, topicName);
        return getTopic(topicFQname);
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
        topicName = normalizeURI(clientSocket, topicName);
        WampTopic topic = getTopic(clientSocket, topicName);
        
        if(topic != null) {
            try { 
                WampModule module = modules.get(topic.getBaseURI());
                if(module != null) module.onSubscribe(clientSocket, topic); 
                clientSocket.addTopic(topic);
                topic.addSocket(clientSocket);
            } catch(Exception ex) {
                logger.log(Level.SEVERE, "Error in subscription to topic", ex);
            }          
        }
        
        return topic;
    }
    
    public WampTopic unsubscribeClientFromTopic(WampSocket clientSocket, String topicName)
    {
        topicName = normalizeURI(clientSocket, topicName);
        WampTopic topic = getTopic(clientSocket, topicName);
        if(topic != null) {
            try { 
                WampModule module = modules.get(topic.getBaseURI());
                if(module != null) module.onUnsubscribe(clientSocket, topic); 
                
                clientSocket.removeTopic(topic);
                topic.removeSocket(clientSocket);
                
            } catch(Exception ex) {
                logger.log(Level.SEVERE, "Error in unsubscription to topic", ex);
            }          
            
        }
        return topic;
    }
        
    
    /**
     * Broadcasts the event to subscribed sockets.
     *
     * @param user the user name
     * @param text the text message
     */
    public void publishEvent(WampSocket clientSocketFromSender, String topicName, JSONObject event, boolean excludeMe) {
        logger.log(Level.INFO, "Preparation for broadcasting to {0}: {1}", new Object[]{topicName,event});
        WampTopic topic = getTopic(clientSocketFromSender, topicName);
        if(topic != null) {
            Set<String> excludedSet = new HashSet<String>();
            if(excludeMe) excludedSet.add(clientSocketFromSender.getSessionId());
            publishEvent(clientSocketFromSender, topicName, event, excludedSet, null);
        }
    }
    
    public void publishEvent(WampSocket clientSocketFromSender, String topicName, JSONObject event, Set<String> excluded, Set<String> eligible) {
        logger.log(Level.INFO, "Broadcasting to {0}: {1}", new Object[]{topicName,event});
        WampTopic topic = getTopic(clientSocketFromSender, topicName);
        
        String msg = "[8,\"" + topic.getURI() + "\", " + event.toString() + "]";
        
        if(eligible == null)  eligible = topic.getSocketIds();
        for (String cid : eligible) {
            if((excluded==null) || (!excluded.contains(cid))) {
                WampSocket socket = topic.getSocket(cid);
                if(socket != null && socket.isConnected() && !excluded.contains(cid)) {
                    try { 
                        WampModule module = modules.get(topic.getBaseURI());
                        if(module != null) module.onPublish(socket, topic, event); 
                        socket.sendSafe(msg);
                    } catch(Exception ex) {
                        logger.log(Level.SEVERE, "Error dispatching event publication to registered module", ex);
                    }          
 
                }
            }
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
