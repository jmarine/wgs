package org.wgs.wamp.topic;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.jms.JMSException;
import javax.jms.TemporaryTopic;

import javax.jms.Topic;
import javax.persistence.Transient;


public class WampTopic implements Topic, TemporaryTopic
{
    private String topicName;
    private WampTopicOptions options;

    @Transient
    private Map<Long,WampSubscription> subscriptions = new ConcurrentHashMap<Long,WampSubscription>();

    public WampTopic() { }
    
    public WampTopic( String topicName, WampTopicOptions options) 
    {
        setTopicName(topicName);
        if(options == null) options = new WampTopicOptions(); // default values
        this.options = options;
    }
    
    public WampTopicOptions getOptions()
    {
        return options;
    }
    
    
    @Override
    public void delete() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }    
    
    /**
     * @return the name
     */
    public String getTopicName() {
        return topicName;
    }

    /**
     * @param name the name to set
     */
    public void setTopicName(String uri) {
        this.topicName = uri;
    }
    
    public String getBaseURI() {
        int pos = topicName.lastIndexOf("#");
        if(pos == -1)  return topicName;
        else return topicName.substring(0, pos+1);
    }

    
    
    /**
     * @return the sockets
     */
    public void addSubscription(WampSubscription subscription) {
        subscriptions.put(subscription.getId(), subscription);
    }

    /**
     * @param socket the sockets to set
     */
    public WampSubscription removeSubscription(Long subscriptionId) {
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

    public int getSubscriptionCount()
    {
        return subscriptions.size();
    }    


}
