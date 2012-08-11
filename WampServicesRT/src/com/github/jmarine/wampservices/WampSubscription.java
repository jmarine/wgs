
package com.github.jmarine.wampservices;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;


public class WampSubscription 
{
    public static final int OPTION_PUBLISHER_ID      = 1;
    public static final int OPTION_EVENT_PERSISTENCE = 2;
    public static final int OPTION_EVENT_HISTORY     = 4;
    

    private int        options;
    
    private WampSocket client;
    
    private WampTopic  topic;
    
    private WampTopicGroup topicGroup;
    
    
    public WampSubscription(WampSocket client, WampTopic topic, int options) 
    {
        this.options = options;
        this.client = client;
        this.topic = topic;
        this.topicGroup = null;
    }
    
    public WampSubscription(WampSocket client, WampTopicGroup topicGroup, int options) 
    {
        this.options = options;
        this.client = client;
        this.topic = null;
        this.topicGroup = topicGroup;
    }
    
    public int getOptions()
    {
        return options;
    }
    
    public WampSocket getSocket()
    {
        return client;
    }
    
    public WampTopic getTopic()
    {
        return topic;
    }
    
    public WampTopicGroup getTopicGroup()
    {
        return topicGroup;
    }
    
    public String getTopicURIs() {
        if(topicGroup != null) {
            return topicGroup.getTopicUriPattern();
        } else {
            return topic.getURI();
        }
    }
    
    
}
