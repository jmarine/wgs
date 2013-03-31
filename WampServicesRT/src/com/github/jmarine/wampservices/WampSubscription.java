
package com.github.jmarine.wampservices;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;


public class WampSubscription 
{
    private WampSocket client;
    
    private String topicUrlOrPattern;

    private WampSubscriptionOptions options;
    
    private JsonNode status;
    
    private int refCount;
    
    
    public WampSubscription(WampSocket client, String topicUrlOrPattern, WampSubscriptionOptions options) 
    {
        if(options == null) options = new WampSubscriptionOptions(null);
        this.options = options;
        this.client = client;
        this.topicUrlOrPattern = topicUrlOrPattern;
        this.status = options.getStatus();
        this.refCount = 0;
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

    public synchronized int refCount(int delta)
    {
        refCount += delta;
        return refCount;
    }

    public JsonNode getStatus() {
        return status;
    }

    public void setStatus(JsonNode status) {
        this.status = status;
    }
    
    
    public ObjectNode toJSON() 
    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode obj = mapper.createObjectNode();
        obj.put("sessionId", client.getSessionId());
        obj.put("status", getStatus());
        return obj;
    }
    
}
