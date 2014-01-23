package org.wgs.wamp.topic;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.persistence.Transient;


public class WampTopic 
{
    private String uri;
    private WampTopicOptions options;

    @Transient
    private Map<Long,WampSubscription> subscriptions = new ConcurrentHashMap<Long,WampSubscription>();

    public WampTopic() { }
    
    public WampTopic( String uri, WampTopicOptions options) 
    {
        setURI(uri);
        if(options == null) options = new WampTopicOptions(); // default values
        this.options = options;
    }
    
    public WampTopicOptions getOptions()
    {
        return options;
    }
    
    /**
     * @return the name
     */
    public String getURI() {
        return uri;
    }

    /**
     * @param name the name to set
     */
    public void setURI(String uri) {
        this.uri = uri;
    }
    
    public String getBaseURI() {
        int pos = uri.lastIndexOf("#");
        if(pos == -1)  return uri;
        else return uri.substring(0, pos+1);
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
