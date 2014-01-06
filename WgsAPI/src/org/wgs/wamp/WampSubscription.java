
package org.wgs.wamp;

import java.util.Collection;
import java.util.Set;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class WampSubscription 
{
    private Long subscriptionId;
    
    private String topicRegExp;
    
    private WampSubscriptionOptions options;

    private HashMap<Long, RefCount<WampSocket>> sockets = new HashMap<Long, RefCount<WampSocket>>();
    
    private Collection<WampTopic> topics = null;
    
    
    public WampSubscription(Long subscriptionId, String topicRegExp, Collection<WampTopic> topics, WampSubscriptionOptions options) 
    {
        this.subscriptionId = subscriptionId;
        this.topicRegExp = topicRegExp;        
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
            ref = new RefCount(socket, 1);
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


class RefCount<T>
{
    private T obj;
    private AtomicInteger counter;
    
    RefCount(T obj, int initialRefCount)
    {
        this.obj = obj;
        this.counter = new AtomicInteger(initialRefCount);
    }
    
    public int refCount(int delta)
    {
        return counter.addAndGet(delta);
    }
    
    public T getObject()
    {
        return obj;
    }
    
}