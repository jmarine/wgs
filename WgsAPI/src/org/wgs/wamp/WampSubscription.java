
package org.wgs.wamp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeMap;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;


public class WampSubscription 
{
    private Long subscriptionId;
    
    private String topicUrlOrPattern;
    
    private WampSubscriptionOptions options;

    private TreeMap<Long, WampSocket> sockets = new TreeMap<Long, WampSocket>();
    
    private Collection<WampTopic> topics = null;
    
    
    public WampSubscription(Long subscriptionId, String topicUrlOrPattern, Collection<WampTopic> topics, WampSubscriptionOptions options) 
    {
        this.subscriptionId = subscriptionId;
        if(options == null) options = new WampSubscriptionOptions(null);
        this.topicUrlOrPattern = topicUrlOrPattern;
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
    
    public String getTopicUriOrPattern() 
    {
        return topicUrlOrPattern;
    }
    
    public WampSubscriptionOptions getOptions()
    {
        return options;
    }
    
}
