
package org.wgs.wamp;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class WampTopicPattern 
{
    private WampSubscriptionOptions.MatchEnum matchType;
    
    private String topicUriPattern;
    
    private Long subscriptionId;
    
    private Collection<WampTopic> topics;
    
    private Map<Long,WampSubscription> subscriptions;

    
    public WampTopicPattern(Long subscriptionId, WampSubscriptionOptions.MatchEnum matchType, String topicUriPattern, Collection<WampTopic> topics) 
    {
        this.subscriptionId = subscriptionId;
        this.matchType =  matchType;
        this.topicUriPattern = topicUriPattern;
        this.topics = topics;
        this.subscriptions = new ConcurrentHashMap<Long,WampSubscription>();
    }
    
    
    /**
     * @return the requestId
     */
    public Long getSubscriptionId() {
        return subscriptionId;
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
        subscriptions.put(subscription.getId(), subscription);
    }
    
    public void removeSubscription(WampSubscription subscription)
    {
        subscriptions.remove(subscription.getId());
    }
    
    public WampSubscription getSubscription(Long subscriptionId) 
    {
        return subscriptions.get(subscriptionId);
    }
    
    public Collection<WampSubscription> getSubscriptions()
    {
        return subscriptions.values();
    } 



   
}
