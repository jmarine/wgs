package com.github.jmarine.wampservices;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import javax.websocket.CloseReason;
import javax.websocket.Session;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.TextNode;


public class WampSocket 
{
    private static final Logger logger = Logger.getLogger(WampSocket.class.toString());
    
    private WampApplication app;
    private boolean connected;
    private Session session;
    private String  sessionId;
    private Map     sessionData;
    private Map<String,String> prefixes;
    private Map<String,WampSubscription> subscriptions;

    public WampSocket(WampApplication app, Session session) 
    {
        this.app = app;
        this.connected = true;
        this.session = session;
        
        sessionId   = UUID.randomUUID().toString();
        sessionData = new ConcurrentHashMap();
        subscriptions = new ConcurrentHashMap<String,WampSubscription>();        
        prefixes    = new HashMap<String,String>();
    }

    /**
     * Get the session ID
     * @return the user name
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Get the session data 
     * @return the user name
     */
    public Map getSessionData() {
        return sessionData;
    }


    public void registerPrefixURL(String prefix, String url)
    {
        prefixes.put(prefix, url);	
    }
    
    public String getPrefixURL(String prefix)
    {
        return prefixes.get(prefix);
    }



    public void addSubscription(WampSubscription subscription)
    {
        subscriptions.put(subscription.getTopicUriOrPattern(), subscription);
    }
    
    public WampSubscription removeSubscription(String topicUriOrPattern)
    {
        return subscriptions.remove(topicUriOrPattern);
    }
    
    public Collection<WampSubscription> getSubscriptions()
    {
        return subscriptions.values();
    }

    
    
    public String normalizeURI(String curie) {
        int curiePos = curie.indexOf(":");
        if(curiePos != -1) {
            String prefix = curie.substring(0, curiePos);
            String baseURI = getPrefixURL(prefix);
            if(baseURI != null) curie = baseURI + curie.substring(curiePos+1);
        }
        return curie;
    }    


    public void sendSafe(String msg) {
        try {
            if(isOpen()) session.getRemote().sendString(msg);
        } catch(Exception e) {
            logger.log(Level.FINE, "Removing wamp client '" + sessionId + "': " + e.getMessage(), e);
            app.onWampClose(session, new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "onError"));
        }
    }

    
    public void sendWampResponse(ArrayNode response) {
        String message = response.toString();
        sendSafe(message);
    }
    
    
    public void close(CloseReason reason)
    {
        this.connected = false;
        app.onWampClose(session, reason);
    }
    
    protected boolean isOpen() {
        return connected && session.isOpen();
    }
    
    
    protected void sendCallResult(String callID, ArrayNode args)
    {
        StringBuilder response = new StringBuilder();
        if(args == null) {
            ObjectMapper mapper = new ObjectMapper();
            args = mapper.createArrayNode();
            args.add((String)null);
        }

        response.append("[");
        response.append("3");
        response.append(",");
        response.append(app.encodeJSON(callID));
        for(int i = 0; i < args.size(); i++) {
            response.append(",");
            try { 
                JsonNode obj = args.get(i); 
                if(obj instanceof TextNode) {
                    response.append(app.encodeJSON(obj.asText()));
                } else {
                    response.append(obj); 
                }
            }
            catch(Exception ex) { response.append("null"); }
        }
        response.append("]");
        sendSafe(response.toString());
    }    
    
    
    protected void sendCallError(String callID, String errorURI, String errorDesc, Object errorDetails)
    {
        if(errorURI == null) errorURI = WampException.WAMP_GENERIC_ERROR_URI;
        if(errorDesc == null) errorDesc = "";

        StringBuilder response = new StringBuilder();
        response.append("[");
        response.append("4");
        response.append(",");
        response.append(app.encodeJSON(callID));

        response.append(",");
        response.append(app.encodeJSON(errorURI));
        response.append(",");
        response.append(app.encodeJSON(errorDesc));
        
        if(errorDetails != null) {
            response.append(",");
            if(errorDetails instanceof String) {
                response.append(app.encodeJSON((String)errorDetails));
            } else {
                response.append(app.encodeJSON(errorDetails.toString()));
            }
        }

        response.append("]");
        sendSafe(response.toString());
    }    
    
    
    /**
     * Broadcasts the event to subscribed sockets.
     */
    public void publishEvent(WampTopic topic, JsonNode event, boolean excludeMe) {
        logger.log(Level.INFO, "Preparation for broadcasting to {0}: {1}", new Object[]{topic.getURI(),event});
        Set<String> excludedSet = new HashSet<String>();
        if(excludeMe) excludedSet.add(this.getSessionId());
        app.publishEvent(this.getSessionId(), topic, event, excludedSet, null);
    }
    

}
