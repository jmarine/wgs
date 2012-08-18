
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
    
    private String     topicUrlOrPattern;
    
    
    public WampSubscription(WampSocket client, String topicUrlOrPattern, int options) 
    {
        this.options = options;
        this.client = client;
        this.topicUrlOrPattern = topicUrlOrPattern;
    }
    
    public int getOptions()
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
