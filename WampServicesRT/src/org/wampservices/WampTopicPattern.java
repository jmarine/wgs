
package org.wampservices;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class WampTopicPattern 
{
    private WampSubscriptionOptions.MatchEnum matchType;
    
    private String topicUriPattern;
    
    private Collection<WampTopic> topics;
    
    private Map<String,WampSubscription> subscriptions;

    
    public WampTopicPattern(WampSubscriptionOptions.MatchEnum matchType, String topicUriPattern, Collection<WampTopic> topics) 
    {
        this.matchType =  matchType;
        this.topicUriPattern = topicUriPattern;
        this.topics = topics;
        this.subscriptions = new ConcurrentHashMap<String,WampSubscription>();
    }
    
    /**
     * @return the matchType
     */
    public WampSubscriptionOptions.MatchEnum getMatchType() {
        return matchType;
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
    
    public WampSubscription getSubscription(String sessionId) 
    {
        return subscriptions.get(sessionId);
    }
    
    public Collection<WampSubscription> getSubscriptions()
    {
        return subscriptions.values();
    } 
    
}
