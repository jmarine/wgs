package org.wgs.wamp;

import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.CloseReason;
import org.jdeferred.Deferred;
import org.wgs.security.User;
import org.wgs.security.WampCRA;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.rpc.WampCallController;
import org.wgs.wamp.rpc.WampCalleeRegistration;
import org.wgs.wamp.rpc.WampInvocation;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.topic.WampPublishOptions;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampTopic;
import org.wgs.wamp.type.WampConnectionState;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


public abstract class WampSocket 
{
    private static final Logger logger = Logger.getLogger(WampSocket.class.toString());

    protected Principal principal;
    protected boolean connected;
    
    private Long    socketId;
    private Long    sessionId;
    private Map<Long,WampSubscription> subscriptions;
    private Map<Long,WampCallController> callControllers;
    private Map<Long,WampInvocation> invocations;
    private Map<Long,WampCalleeRegistration> rpcRegistrations;
    private WampConnectionState state;
    private int versionSupport;
    private WampEncoding wampEncoding;
    private WampDict helloDetails;
    private String realm;
    private boolean goodbyeRequested;
    private String authMethod;
    private String authProvider;
    private AtomicLong nextRequestId;
    

    public WampSocket() 
    {
        this.authMethod = "anonymous";
        this.versionSupport = 1;
        this.connected = true;
        this.nextRequestId = new AtomicLong(0L);
        
        socketId = WampProtocol.newGlobalScopeId();
        subscriptions = new ConcurrentHashMap<Long,WampSubscription>();        
        invocations = new ConcurrentHashMap<Long,WampInvocation>();
        callControllers = new java.util.concurrent.ConcurrentHashMap<Long,WampCallController>();
        rpcRegistrations = new java.util.concurrent.ConcurrentHashMap<Long,WampCalleeRegistration>();
    }
    
    public void init() {
        this.principal = getUserPrincipal();
        this.state = (this.principal == null) ? WampConnectionState.ANONYMOUS : WampConnectionState.AUTHENTICATED;
        String subprotocol = getNegotiatedSubprotocol();
        if(subprotocol != null) {
            switch(subprotocol) {
                case "wamp":
                    setVersionSupport(WampApplication.WAMPv1);
                    setEncoding(WampEncoding.JSON);
                    break;
                    
                case "wamp.2.msgpack":
                    setVersionSupport(WampApplication.WAMPv2);
                    setEncoding(WampEncoding.MsgPack);
                    break;                    
                    
                case "wamp.2.msgpack.batched":
                    setVersionSupport(WampApplication.WAMPv2);
                    setEncoding(WampEncoding.BatchedMsgPack);
                    break;                    
                    
                case "wamp.2.json":
                    setVersionSupport(WampApplication.WAMPv2);
                    setEncoding(WampEncoding.JSON);
                    break;
                    
                case "wamp.2.json.batched":
                    setVersionSupport(WampApplication.WAMPv2);
                    setEncoding(WampEncoding.BatchedJSON);
                    break;

                default:  // use "wamp.2.json" by default (fix for WildFly 8.0.0)
                    setVersionSupport(WampApplication.WAMPv2);
                    setEncoding(WampEncoding.JSON);
                    break;
                    
            }        
        }
        
    }
    
    /**
     * Get the socket ID
     * @return the socket ID
     */
    public Long getSocketId() {
        return socketId;
    }    
    
    /**
     * Get the WAMP session ID
     * @return the WAMP session ID, or null if the session has not been welcomed
     */
    public Long getWampSessionId() {
        return sessionId;
    }
    
    public void setWampSessionId(Long id) {
        this.sessionId = id;
    }
    
    /**
     * Get a new request ID (session scope).
     * 
     * @return a request ID.
     */
    public long getNextRequestId()
    {
        return nextRequestId.incrementAndGet();
    }

    /**
     * Get the session ID
     * @return the user name
     */
    public String getAuthId() {
        if(principal != null && principal instanceof User) {
            User usr = (User)principal;
            return usr.getLogin();
        } else {
            return (String)this.getSessionData(WampCRA.WAMP_AUTH_ID_PROPERTY_NAME);
        }
    }
    
    
    public String getAuthRole() {
        if(principal != null && principal instanceof User) {
            User usr = (User)principal;
            return (usr.isAdministrator()? "admin" : "user");
        } else {
            return "anonymous";
        }
    }
    
    
    public abstract String getNegotiatedSubprotocol();
    
    /**
     * Get the session data 
     * @return the user name
     */
    public abstract Object  getSessionData(String key);
    public abstract void    putSessionData(String key, Object val);
    public abstract Object  removeSessionData(String key);
    public abstract boolean containsSessionData(String key);

    /**
     * Get the user principal
     * @return the user principal
     */
    public Principal getUserPrincipal()
    {
        return this.principal;
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
        return realm;
    }
    
    public void setRealm(String realm)
    {
        this.realm = realm;
    }
    
    
    public void setHelloDetails(WampDict helloDetails)
    {
        this.helloDetails = helloDetails;
    }
    
    public WampDict getHelloDetails()
    {
        return helloDetails;
    }    
    
    public void setAuthMethod(String authMethod)
    {
        this.authMethod = authMethod;
    }
    
    public String getAuthMethod()
    {
        return this.authMethod;
    }
    
    
    public void setAuthProvider(String authProvider)
    {
        this.authProvider = authProvider;
    }
    
    public String getAuthProvider()
    {
        if(this.authProvider != null) {
            return this.authProvider;
        } else {
            String realm = this.getRealm();
            return "realm:" + realm;
        }
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

    
    
    public void addCallController(Long callID, WampCallController controller)
    {
        callControllers.put(callID, controller);
    }
    
    public WampCallController getCallController(Long callID)
    {
        return callControllers.get(callID);
    }    
    
    public WampCallController removeCallController(Long callID) 
    {
        return callControllers.remove(callID);
    }    
    
    
    
    
    public void addInvocation(Long invocationId,  WampCallController controller, Deferred<WampResult, WampException, WampResult> rpcAsyncCallback)
    {
        invocations.put(invocationId, new WampInvocation(invocationId, controller, rpcAsyncCallback));
    }
    
    public WampInvocation getInvocation(Long invocationId)
    {
        return invocations.get(invocationId);
    }    
    
    public WampInvocation removeInvocation(Long invocationId) 
    {
        return invocations.remove(invocationId);
    }

    public Set<Long> getInvocationIDs()
    {
        return invocations.keySet();
    }
    
    
    public abstract void sendObject(Object msg);
   
    
    public boolean close(CloseReason reason)
    {
        if(isOpen()) {
            WampProtocol.sendGoodbyeMessage(this, null, reason.getReasonPhrase());

            this.connected = false;
            
            return true;
        } else {
            return false;
        }
    }
    
    public boolean isOpen() 
    {
        return connected;
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
        if(excludeMe) excludedSet.add(this.getWampSessionId());
        WampPublishOptions options = new WampPublishOptions();
        options.setExcludeMe(excludeMe);
        options.setExcluded(excludedSet);
        options.setDiscloseMe(identifyMe);
        
        WampDict eventDetails = options.toWampObject();
        if(options.hasDiscloseMe()) {
            eventDetails.put("publisher", this.getWampSessionId());
            eventDetails.put("authid", this.getAuthId());
            eventDetails.put("authprovider", this.getAuthProvider());
            eventDetails.put("authrole", this.getAuthRole());
        }           

        WampBroker.publishEvent(this.getRealm(), WampProtocol.newGlobalScopeId(), topic, payload, payloadKw, options.getEligible(), options.getExcluded(), eventDetails, true);
        
    }

    
}
