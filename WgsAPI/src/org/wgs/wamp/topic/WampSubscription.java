
package org.wgs.wamp.topic;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.wgs.util.RefCount;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.type.WampMatchType;


public class WampSubscription 
{
    private Long subscriptionId;
    
    private String topicRegExp;
    
    private WampSubscriptionOptions options;

    private HashMap<Long, RefCount<WampSocket>> sockets = new HashMap<Long, RefCount<WampSocket>>();
    
    private Collection<WampTopic> topics = null;
    
    
    public WampSubscription(Long subscriptionId, WampMatchType matchType, String topicUriOrPattern, Collection<WampTopic> topics, WampSubscriptionOptions options) 
    {
        this.subscriptionId = subscriptionId;
        this.topicRegExp = WampBroker.getPatternRegExp(matchType, topicUriOrPattern);        
        this.topics  = topics;
        this.options = (options != null)? options : new WampSubscriptionOptions(null);
    }
    
    
    public Long getId()
    {
        return subscriptionId;
    }

    public Collection<WampTopic>  getTopics()
    {
        return topics;
    }

    
    public synchronized boolean addSocket(WampSocket socket)
    {
        RefCount<WampSocket> ref = sockets.get(socket.getSessionId());
        if(ref == null) {
            ref = new RefCount<WampSocket>(socket, 1);
            sockets.put(socket.getSessionId(), ref);
            return true;
        } else {
            ref.refCount(+1);
            return false;
        }
    }
    
    public synchronized WampSocket removeSocket(Long sessionId)
    {
        RefCount<WampSocket> ref = sockets.get(sessionId);
        if(ref == null) {
            return null;
        } else {
            if(ref.refCount(-1) == 0) {
                sockets.remove(sessionId);
            }
            return ref.getObject();
        }
    }
    
    public WampSocket getSocket(Long sessionId)
    {
        return sockets.get(sessionId).getObject();
    }
    

    
    public int getSocketsCount()
    {
        return sockets.size();
    }
    
    public Set<Long> getSessionIds(String realm)
    {
        if(realm == null) {
            return sockets.keySet();
        } else {
            HashSet<Long> sids = new HashSet<Long>();
            for(RefCount<WampSocket> refSocket : sockets.values()) {
                WampSocket socket = refSocket.getObject();
                if(realm.equals(socket.getRealm())) sids.add(socket.getSessionId());
            }
            return sids;
        }
    }
    
    public String getTopicRegExp() 
    {
        return topicRegExp;
    }
    
    public WampSubscriptionOptions getOptions()
    {
        return options;
    }
    
}
