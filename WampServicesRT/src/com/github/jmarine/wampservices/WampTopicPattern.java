
package com.github.jmarine.wampservices;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class WampTopicPattern 
{
    private String     topicUriPattern;
    
    private Collection<WampTopic> topics;
    
    private Map<String,WampSubscription> subscriptions;

    
    public WampTopicPattern(String topicUriPattern, Collection<WampTopic> topics) 
    {
        this.topicUriPattern = topicUriPattern;
        this.topics = topics;
        this.subscriptions = new ConcurrentHashMap<String,WampSubscription>();
    }
    
    public String getTopicUriPattern()
    {
        return topicUriPattern;
    }
    
    public Collection<WampTopic> getTopics()
    {
        return topics;
    }    
    
    public void addSubscription(WampSubscription subscription)
    {
        subscriptions.put(subscription.getSocket().getSessionId(), subscription);
    }
    
    public void removeSubscription(WampSubscription subscription)
    {
        subscriptions.remove(subscription.getSocket().getSessionId());
    }
    
    public Collection<WampSubscription> getSubscriptions()
    {
        return subscriptions.values();
    }    
    
    public Collection<String> getSessionIds()
    {
        return subscriptions.keySet();
    }        
    
}
