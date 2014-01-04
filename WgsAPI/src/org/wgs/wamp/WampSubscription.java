
package org.wgs.wamp;

import java.util.Collection;
import java.util.Set;
import java.util.TreeMap;


public class WampSubscription 
{
    private Long subscriptionId;
    
    private String topicRegExp;
    
    private WampSubscriptionOptions options;

    private TreeMap<Long, WampSocket> sockets = new TreeMap<Long, WampSocket>();
    
    private Collection<WampTopic> topics = null;
    
    
    public WampSubscription(Long subscriptionId, String topicRegExp, Collection<WampTopic> topics, WampSubscriptionOptions options) 
    {
        this.subscriptionId = subscriptionId;
        if(options == null) options = new WampSubscriptionOptions(null);
        this.topicRegExp = topicRegExp;
        this.topics = topics;
    }
    
    
    public Long getId()
    {
        return subscriptionId;
    }

    public Collection<WampTopic>  getTopics()
    {
        return topics;
    }

    
    public boolean addSocket(WampSocket socket)
    {
        return (sockets.put(socket.getSessionId(), socket) == null);
    }
    
    public WampSocket removeSocket(Long sessionId)
    {
        return sockets.remove(sessionId);
    }
    
    public WampSocket getSocket(Long sessionId)
    {
        return sockets.get(sessionId);
    }
    
    public Collection<WampSocket> getSockets()
    {
        return sockets.values();
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
