
package com.github.jmarine.wampservices;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;


public class WampSubscription 
{
    private WampSocket client;
    
    private String     topicUrlOrPattern;

    private WampSubscriptionOptions options;
    
    
    public WampSubscription(WampSocket client, String topicUrlOrPattern, WampSubscriptionOptions options) 
    {
        if(options == null) options = new WampSubscriptionOptions();
        this.options = options;
        this.client = client;
        this.topicUrlOrPattern = topicUrlOrPattern;
    }
    
    public WampSubscriptionOptions getOptions()
    {
        return options;
    }
    
    public WampSocket getSocket()
    {
        return client;
    }
    
    public String getTopicUriOrPattern() 
    {
        return topicUrlOrPattern;
    }
    
}
