package org.wgs.wamp.topic;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.jms.JMSException;
import jakarta.jms.TemporaryTopic;
import jakarta.jms.Topic;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;


@Entity(name="Topic")
@Table(name="TOPIC")
@NamedQueries({
    @NamedQuery(name="wgs.findAllTopics",query="SELECT OBJECT(t) FROM Topic t")
})
public class WampTopic implements Topic, TemporaryTopic
{
    @Id
    private String topicName;
    
    @Transient
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
        WampBroker.removeTopic(null, topicName);
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
    
    @Override
    public int hashCode()
    {
        return this.topicName.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if(obj != null && obj instanceof WampTopic) {
            WampTopic topic = (WampTopic)obj;
            return this.topicName.equals(topic.getTopicName());
        }
        return false;
    }
    
    @Override
    public String toString()
    {
        return getTopicName();
    }

}
