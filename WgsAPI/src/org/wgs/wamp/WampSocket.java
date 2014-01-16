package org.wgs.wamp;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.websocket.CloseReason;
import javax.websocket.Session;


public class WampSocket 
{
    private static final Logger logger = Logger.getLogger(WampSocket.class.toString());

    private WampApplication app;
    private boolean connected;
    private Session session;
    private Long    sessionId;
    private Map     sessionData;
    private Map<String,String> prefixes;
    private Map<Long,WampSubscription> subscriptions;
    private Map<Long,Promise> rpcPromises;
    private Map<Long,WampCallController> rpcController;
    private WampConnectionState state;
    private Principal principal;
    private long incomingHeartbeatSeq;
    private AtomicLong outgoingHeartbeatSeq;
    private int versionSupport;
    private WampEncoding wampEncoding;
    private WampDict helloDetails;
    

    public WampSocket(WampApplication app, Session session) 
    {
        this.incomingHeartbeatSeq = 0L;
        this.outgoingHeartbeatSeq = new AtomicLong(0L);
        this.versionSupport = 1;
        this.app = app;
        this.connected = true;
        this.session = session;
        this.principal = session.getUserPrincipal();
        this.state = (principal != null) ? WampConnectionState.AUTHENTICATED : WampConnectionState.ANONYMOUS;
        
        sessionId   = WampProtocol.newId();
        sessionData = new ConcurrentHashMap();
        subscriptions = new ConcurrentHashMap<Long,WampSubscription>();        
        prefixes    = new HashMap<String,String>();
        rpcPromises = new HashMap<Long,Promise>();
        rpcController = new HashMap<Long,WampCallController>();
        
        String subprotocol = session.getNegotiatedSubprotocol();
        if(subprotocol != null) {
            switch(subprotocol) {
                case "wamp":
                    setVersionSupport(WampApplication.WAMPv1);
                    setEncoding(WampEncoding.JSon);
                    break;
                case "wamp.2.json":
                    setVersionSupport(WampApplication.WAMPv2);
                    setEncoding(WampEncoding.JSon);
                    break;
                case "wamp.2.msgpack":
                    setVersionSupport(WampApplication.WAMPv2);
                    setEncoding(WampEncoding.MsgPack);
                    break;
            }        
        }
    }

    /**
     * Get the session ID
     * @return the user name
     */
    public Long getSessionId() {
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
        if(this.principal != null) return this.principal;
        else return this.session.getUserPrincipal();
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
    
    
    public void setHelloDetails(WampDict helloDetails)
    {
        this.helloDetails = helloDetails;
    }
    
    public boolean supportProgressiveCalls()
    {
        boolean retval = true;
        if(helloDetails != null) {
            if(helloDetails.has("roles")) {
                WampDict rolesDetails = (WampDict)helloDetails.get("roles");
                if(rolesDetails.has("callee")) {
                    WampDict callerDetails = (WampDict)rolesDetails.get("caller");
                    if(callerDetails.has("progressive")) {
                        retval = callerDetails.get("progressive").asBoolean();
                    }
                }
            }
        }
        return retval;
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
        subscriptions.put(subscription.getId(), subscription);
    }
    
    public WampSubscription removeSubscription(Long subscriptionId)
    {
        return subscriptions.remove(subscriptionId);
    }
    
    public WampSubscription getSubscription(Long subscriptionId)
    {
        return subscriptions.get(subscriptionId);
    }    
    
    public Collection<WampSubscription> getSubscriptions()
    {
        return subscriptions.values();
    }

    
    public void addRpcPromise(Long callID, Promise rpcPromise)
    {
        rpcPromises.put(callID, rpcPromise);
    }
    
    public Promise getRpcPromise(Long callID)
    {
        return rpcPromises.get(callID);
    }    
    
    public Promise removeRpcPromise(Long callID) 
    {
        return rpcPromises.remove(callID);
    }
    
    
    public void addRpcController(Long callID, WampCallController controller)
    {
        rpcController.put(callID, controller);
    }
    
    public WampCallController getRpcController(Long callID)
    {
        return rpcController.get(callID);
    }    
    
    public WampCallController removeRpcController(Long callID) 
    {
        return rpcController.remove(callID);
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
    
    
    public void sendWampMessage(WampList args)
    {
        try {        
            Object msg = WampObject.getSerializer(getEncoding()).serialize(args);
            sendObject(msg);

        } catch(Exception e) {
            logger.log(Level.FINE, "Removing wamp client '" + sessionId + "': " + e.getMessage(), e);
            app.onWampClose(session, new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "onError"));
        }

    }


    public synchronized void sendObject(Object msg) {
        try {
            if(isOpen()) {
                session.getBasicRemote().sendObject(msg);

                /*
                switch(getEncoding()) {
                    case JSon:
                        session.getBasicRemote().sendText(msg.toString());
                        break;
                    case MsgPack:
                        byte[] src = (byte[])msg;
                        ByteBuffer buffer = ByteBuffer.allocate(src.length);
                        buffer.put(src);
                        session.getBasicRemote().sendBinary(buffer);
                        break;
                }
                */

            }
        } catch(Exception e) {
            logger.log(Level.FINE, "Removing wamp client '" + sessionId + "': " + e.getMessage(), e);
            app.onWampClose(session, new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "onError"));
        }
    }

    
    public void close(CloseReason reason)
    {
        this.connected = false;
        app.onWampClose(session, reason);
    }
    
    public boolean isOpen() {
        return connected && session.isOpen();
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
    
    
    public WampEncoding getEncoding()
    {
        return wampEncoding;
    }
    
    public void setEncoding(WampEncoding wampEncoding)
    {
        this.wampEncoding = wampEncoding;
    }
    

    public long getIncomingHeartbeat()
    {
        return this.incomingHeartbeatSeq;
    }
    
    public void setIncomingHeartbeat(long heartbeatSequenceNo) 
    {
        this.incomingHeartbeatSeq = heartbeatSequenceNo;
    }   
    
    public long getNextOutgoingHeartbeatSeq()
    {
        return outgoingHeartbeatSeq.incrementAndGet();
    }
    
    public void sendHeartbeatMessage(String discard)
    {
        WampProtocol.sendHeartbeatMessage(this, discard);
    }
    
    /**
     * Broadcasts the event to subscribed sockets.
     */
    public void publishEvent(WampTopic topic, WampList payload, WampDict payloadKw, boolean excludeMe, boolean identifyMe) {
        logger.log(Level.INFO, "Preparation for broadcasting to {0}: {1},{2}", new Object[]{topic.getURI(),payload,payloadKw});
        Set<Long> excludedSet = new HashSet<Long>();
        if(excludeMe) excludedSet.add(this.getSessionId());
        WampPublishOptions options = new WampPublishOptions();
        options.setExcludeMe(excludeMe);
        options.setExcluded(excludedSet);
        options.setDiscloseMe(identifyMe);
        WampServices.publishEvent(WampProtocol.newId(), this.getSessionId(), topic, payload, payloadKw, options);
    }


    public WampObject toWampObject()
    {
        WampDict retval = new WampDict();
        retval.put("sessionId", sessionId);
        return retval;
    }
    
}
