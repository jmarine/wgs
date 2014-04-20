package org.wgs.wamp;

import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.websocket.CloseReason;
import javax.websocket.Session;

import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.rpc.WampCallController;
import org.wgs.wamp.rpc.WampAsyncCallback;
import org.wgs.wamp.rpc.WampCalleeRegistration;
import org.wgs.wamp.topic.WampTopic;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampPublishOptions;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.type.WampConnectionState;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


public class WampSocket 
{
    private static final String LOCAL_DOMAIN = "";
    private static final Logger logger = Logger.getLogger(WampSocket.class.toString());

    private WampApplication app;
    private boolean connected;
    private Session session;
    private Long    sessionId;
    private Map<String,Object> sessionData;
    private Map<String,String> prefixes;
    private Map<Long,WampSubscription> subscriptions;
    private Map<Long,WampCallController> rpcController;
    private Map<Long,WampAsyncCallback> rpcAsyncCallbacks;
    private Map<Long,WampCalleeRegistration> rpcRegistrations;
    private WampConnectionState state;
    private Principal principal;
    private long incomingHeartbeatSeq;
    private AtomicLong outgoingHeartbeatSeq;
    private int versionSupport;
    private WampEncoding wampEncoding;
    private WampDict helloDetails;
    private String realm;
    private boolean goodbyeRequested;
    

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
        sessionData = new ConcurrentHashMap<String,Object>();
        subscriptions = new ConcurrentHashMap<Long,WampSubscription>();        
        prefixes    = new HashMap<String,String>();
        rpcAsyncCallbacks = new ConcurrentHashMap<Long,WampAsyncCallback>();
        rpcController = new HashMap<Long,WampCallController>();
        rpcRegistrations = new java.util.concurrent.ConcurrentHashMap<Long,WampCalleeRegistration>();
        
        String subprotocol = session.getNegotiatedSubprotocol();
        System.out.println("DEBUG: WampSocket: negotiated subprotocol: " + subprotocol);
        if(subprotocol != null) {
            switch(subprotocol) {
                case "wamp":
                    setVersionSupport(WampApplication.WAMPv1);
                    setEncoding(WampEncoding.JSon);
                    break;
                    
                case "wamp.2.msgpack":
                    setVersionSupport(WampApplication.WAMPv2);
                    setEncoding(WampEncoding.MsgPack);
                    break;                    
                    
                case "wamp.2.json":
                    setVersionSupport(WampApplication.WAMPv2);
                    setEncoding(WampEncoding.JSon);
                    break;

                default:    // FIXME: negotiated subprotocol doesn't work in WildFly 8.0.0.
                    setVersionSupport(WampApplication.WAMPv2);
                    setEncoding(WampEncoding.JSon);
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
    public Map<String,Object> getSessionData() {
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
    
    
    public String getRealm()
    {
        return (realm == null || realm.equals("null") || realm.equals("localhost")) ? LOCAL_DOMAIN : realm;
    }
    
    public void setRealm(String realm)
    {
        this.realm = (realm == null || realm.equals("null") || realm.equals("localhost")) ? LOCAL_DOMAIN : realm;
    }
    
    
    public void setHelloDetails(WampDict helloDetails)
    {
        this.helloDetails = helloDetails;
    }
    
    public boolean supportsProgressiveCallResults()
    {
        boolean retval = true;
        if(helloDetails != null) {
            if(helloDetails.has("roles")) {
                WampDict roles = (WampDict)helloDetails.get("roles");
                if(roles.has("callee")) {
                    WampDict caller = (WampDict)roles.get("caller");
                    if(caller.has("features")) {
                        WampDict callerFeatures = (WampDict)caller.get("features");
                        if(callerFeatures.has("progressive_call_results")) {
                            retval = callerFeatures.getBoolean("progressive_call_results");
                        } 
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

    
    public void addAsyncCallback(Long callID, WampAsyncCallback rpcAsyncCallback)
    {
        rpcAsyncCallbacks.put(callID, rpcAsyncCallback);
    }
    
    public WampAsyncCallback getAsyncCallback(Long callID)
    {
        return rpcAsyncCallbacks.get(callID);
    }    
    
    public WampAsyncCallback removeAsyncCallback(Long callID) 
    {
        return rpcAsyncCallbacks.remove(callID);
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
    
    
    synchronized void sendObject(Object msg) {
        try {
            if(isOpen()) {
                switch(getEncoding()) {
                    case JSon:
                        session.getBasicRemote().sendText(msg.toString());
                        break;
                    case MsgPack:
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap((byte[])msg));
                        break;
                    default:
                        session.getBasicRemote().sendObject(msg);
                }
            }
        } catch(Exception e) {
            logger.log(Level.FINE, "Removing wamp client '" + sessionId + "': " + e.getMessage(), e);
            close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "wamp.close.error"));
        }
    }

    
    public void close(CloseReason reason)
    {
        if(isOpen()) {
            WampProtocol.sendGoodBye(this, null, reason.getReasonPhrase());

            this.connected = false;
            
            try { session.close(reason); } 
            catch(Exception ex) { }
        }
        
    }
    
    public boolean isOpen() 
    {
        return connected && session.isOpen();
    }
    
    
    public boolean  isGoodbyeRequested()
    {
        return goodbyeRequested;
    }
    
    public void setGoodbyeRequested(boolean goodbyeRequested)
    {
        this.goodbyeRequested = goodbyeRequested;
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
    
    
    public void addRpcRegistration(WampCalleeRegistration registration)
    {
        rpcRegistrations.put(registration.getId(), registration);
    }
    
    public WampCalleeRegistration removeRpcRegistration(Long registrationId)
    {
        return rpcRegistrations.remove(registrationId);
    }
    
    public Collection<WampCalleeRegistration> getRpcRegistrations()
    {
        return rpcRegistrations.values();
    }
    
    /**
     * Broadcasts the event to subscribed sockets.
     */
    public void publishEvent(WampTopic topic, WampList payload, WampDict payloadKw, boolean excludeMe, boolean identifyMe) throws Exception
    {
        logger.log(Level.INFO, "Preparation for broadcasting to {0}: {1},{2}", new Object[]{topic.getTopicName(),payload,payloadKw});
        Set<Long> excludedSet = new HashSet<Long>();
        if(excludeMe) excludedSet.add(this.getSessionId());
        WampPublishOptions options = new WampPublishOptions();
        options.setExcludeMe(excludeMe);
        options.setExcluded(excludedSet);
        options.setDiscloseMe(identifyMe);

        WampBroker.publishEvent(WampProtocol.newId(), topic, payload, payloadKw, options.getEligible(), options.getExcluded(), (options.hasDiscloseMe()? this.getSessionId() : null));
    }

    
}
