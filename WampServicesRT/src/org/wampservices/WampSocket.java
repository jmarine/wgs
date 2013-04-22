package org.wampservices;

import org.wampservices.entity.User;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
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
    private Map<String,Future<?>> rpcFutureResults;
    private WampConnectionState state;
    private Principal principal;
    private int versionSupport;
    private int lastHeartBeat;
    

    public WampSocket(WampApplication app, Session session) 
    {
        this.lastHeartBeat = 0;
        this.versionSupport = 1;
        this.app = app;
        this.connected = true;
        this.session = session;
        this.principal = session.getUserPrincipal();
        this.state = (principal != null) ? WampConnectionState.AUTHENTICATED : WampConnectionState.ANONYMOUS;
        
        sessionId   = UUID.randomUUID().toString();
        sessionData = new ConcurrentHashMap();
        subscriptions = new ConcurrentHashMap<String,WampSubscription>();        
        prefixes    = new HashMap<String,String>();
        rpcFutureResults = new HashMap<String,Future<?>>();
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

    /**
     * Get the user principal
     * @return the user principal
     */
    public Principal getUserPrincipal()
    {
        return this.session.getUserPrincipal();
    }
    
    /**
     * Set the user principal 
     * @param principal the user principal
     */
    public void setUserPrincipal(Principal principal)
    {
        this.principal = principal;
    }    
    
    /**
     * @return the state
     */
    public WampConnectionState getState() {
        return state;
    }
    

    /**
     * @param state the state to set
     */
    public void setState(WampConnectionState state) {
        this.state = state;
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

    
    public void addRpcFutureResult(String callID, Future<?> futureResult)
    {
        rpcFutureResults.put(callID, futureResult);
    }
    
    public Future<?> getRpcFutureResult(String callID)
    {
        return rpcFutureResults.get(callID);
    }    
    
    public Future<?> removeRpcFutureResult(String callID) {
        return rpcFutureResults.remove(callID);
    }
    
    public boolean cancelRpcFutureResult(String callID)
    {
        boolean success = false;
        Future<?> future = rpcFutureResults.get(callID);
        if (future != null) {
            success = future.isDone() || future.cancel(true);
            if(success) rpcFutureResults.remove(callID);
        }
        return success;
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


    public synchronized void sendSafe(String msg) {
        try {
            if(isOpen()) session.getBasicRemote().sendText(msg);
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
    
    
    protected void sendCallResult(int callResponseMsgType, String callID, ArrayNode args)
    {
        StringBuilder response = new StringBuilder();
        if(args == null) {
            ObjectMapper mapper = new ObjectMapper();
            args = mapper.createArrayNode();
            args.add((String)null);
        }

        response.append("[");
        response.append(callResponseMsgType);
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
    
    
    protected void sendCallError(int callErrorMsgType, String callID, String errorURI, String errorDesc, Object errorDetails)
    {
        if(errorURI == null) errorURI = WampException.WAMP_GENERIC_ERROR_URI;
        if(errorDesc == null) errorDesc = "";

        StringBuilder response = new StringBuilder();
        response.append("[");
        response.append(callErrorMsgType);
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
    
    public int getWampVersion() {
        return versionSupport;
    }
    
    public boolean supportVersion(int version) {
        return versionSupport >= version;
    }
    
    public void setVersionSupport(int version) {
        this.versionSupport = version;
    }
    
    public int getLastHeartBeat() {
        return this.lastHeartBeat;
    }

    public void setLastHeartBeat(int heartbeatSequenceNo) {
        this.lastHeartBeat = heartbeatSequenceNo;
    }    
    
    /**
     * Broadcasts the event to subscribed sockets.
     */
    public void publishEvent(WampTopic topic, JsonNode event, boolean excludeMe, boolean identifyMe) {
        logger.log(Level.INFO, "Preparation for broadcasting to {0}: {1}", new Object[]{topic.getURI(),event});
        Set<String> excludedSet = new HashSet<String>();
        if(excludeMe) excludedSet.add(this.getSessionId());
        WampPublishOptions options = new WampPublishOptions();
        options.setExcludeMe(excludeMe);
        options.setIdentifyMe(identifyMe);
        app.publishEvent(this.getSessionId(), topic, event, options);
    }

    

}
