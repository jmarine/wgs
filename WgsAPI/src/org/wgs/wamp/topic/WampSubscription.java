
package org.wgs.wamp.topic;

import java.util.Collection;
import java.util.Set;
import java.util.HashMap;
import org.wgs.util.RefCount;
import org.wgs.wamp.types.WampMatchType;
import org.wgs.wamp.WampSocket;


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
    
    public Set<Long> getSessionIds()
    {
        return sockets.keySet();
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
