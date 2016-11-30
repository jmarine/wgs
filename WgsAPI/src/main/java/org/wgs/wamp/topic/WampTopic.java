package org.wgs.wamp.topic;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.jms.JMSException;
import javax.jms.TemporaryTopic;

import javax.jms.Topic;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Transient;
import org.wgs.util.Storage;


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
