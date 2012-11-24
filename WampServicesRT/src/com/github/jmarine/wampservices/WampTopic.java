package com.github.jmarine.wampservices;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class WampTopic 
{
    private String uri;
    private WampTopicOptions options;
    private Map<String,WampSubscription> subscriptions = new ConcurrentHashMap<String,WampSubscription>();

    
    public WampTopic(WampTopicOptions options) {
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
        int pos = uri.indexOf("#");
        if(pos == -1)  return uri;
        else return uri.substring(0, pos+1);
    }

    /**
     * @return the sockets
     */
    public void addSubscription(WampSubscription subscription) {
        WampSocket socket = subscription.getSocket();
        subscriptions.put(socket.getSessionId(), subscription);
    }

    /**
     * @param socket the sockets to set
     */
    public WampSubscription removeSubscription(String sessionId) {
        return subscriptions.remove(sessionId);
    }
    
    public WampSubscription getSubscription(String sessionId)
    {
        return subscriptions.get(sessionId);
    }
    
    public Collection<WampSubscription> getSubscriptions()
    {
        return subscriptions.values();
    }

    public Set<String> getSessionIds()
    {
        return subscriptions.keySet();
    } 
    
    public int getSubscriptionCount()
    {
        return subscriptions.size();
    }    

}
