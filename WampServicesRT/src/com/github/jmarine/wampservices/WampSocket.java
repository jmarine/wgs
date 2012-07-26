package com.github.jmarine.wampservices;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.grizzly.websockets.DefaultWebSocket;
import com.sun.grizzly.websockets.ProtocolHandler;
import com.sun.grizzly.websockets.WebSocketException;
import com.sun.grizzly.websockets.WebSocketListener;

public class WampSocket extends DefaultWebSocket
{
    // TODO: topic subscription management
    
    private static final Logger logger = Logger.getLogger(WampSocket.class.toString());
    
    private String sessionId;
    private Map    sessionData;
    private Map<String,String> prefixes;
    private Map<String,WampTopic> topics;

    public WampSocket(ProtocolHandler protocolHandler,
                         //HttpRequestPacket request,
                         WebSocketListener... listeners) 
    {
        super(protocolHandler, listeners);
        sessionId   = UUID.randomUUID().toString();
        sessionData = new ConcurrentHashMap();
        topics      = new ConcurrentHashMap<String,WampTopic>();        
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

    public void addTopic(WampTopic topic)
    {
        topics.put(topic.getURI(), topic);
    }
    
    public void removeTopic(WampTopic topic)
    {
        topics.remove(topic.getURI());
    }
    
    public Map<String,WampTopic> getTopics()
    {
        return topics;
    }

    /**
     * Send the message in JSON encoding acceptable by browser's javascript.
     *
     * @param user the user name
     * @param text the text message
     */
    public void sendSafe(String msg) {
        try {
            super.send(msg);
        } catch (WebSocketException e) {
            logger.log(Level.SEVERE, "Removing wamp client: " + e.getMessage(), e);
            close(PROTOCOL_ERROR, e.getMessage());
        }
    }

}
