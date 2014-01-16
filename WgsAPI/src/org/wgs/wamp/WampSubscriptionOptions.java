package org.wgs.wamp;

import java.util.HashSet;
import java.util.List;
import org.codehaus.jackson.JsonNode;


public class WampSubscriptionOptions 
{

    private MatchEnum    matchType;
    private boolean      eventsEnabled;
    private HashSet<String> metaTopics;
    

    public WampSubscriptionOptions(WampDict node) 
    {
        this.matchType = MatchEnum.exact;
        this.eventsEnabled = true;
        this.metaTopics = new HashSet<String>();
        
        if(node != null) {
            if(node.has("match")) {
                setMatchType(MatchEnum.valueOf(node.get("match").asText()));
            }     
            
            if(node.has("metatopics")) {
                setMetaTopics((WampList)node.get("metatopics"));
            }

            if(node.has("metaonly")) {
                setEventsEnabled(!node.get("metaonly").asBoolean().booleanValue());
            }     

        }
    }
    
    /**
     * @return the matchType
     */
    public MatchEnum getMatchType() {
        return matchType;
    }

    /**
     * @param matchType the matchType to set
     */
    public void setMatchType(MatchEnum matchType) {
        this.matchType = matchType;
    }

    /**
     * @return the metaTopics
     */
    public HashSet<String> getMetaTopics() {
        return metaTopics;
    }

    
    /**
     * @param metaTopics the metaTopics to set
     */
    public void setMetaTopics(HashSet<String> metaTopics) {
        this.metaTopics = metaTopics;
    }        
    
    /**
     * @param metaTopics the metaTopics to set
     */
    public void setMetaTopics(WampList metaTopics) {
        this.metaTopics = new HashSet<String>();
        if(metaTopics != null) {
            for(int i = 0; i < metaTopics.size(); i++) {
                this.metaTopics.add(metaTopics.get(i).asText());
            }
        }
    }    



    public boolean hasMetaTopics()
    {
        return (metaTopics.size() > 0);
    }
        
    
    public boolean hasMetaTopic(String metatopic)
    {
        return (metaTopics.contains(metatopic));
    }
    
    
    public void setEventsEnabled(boolean eventsEnabled) {
        this.eventsEnabled = eventsEnabled;
    }
    
    public boolean hasEventsEnabled()
    {
        return (this.eventsEnabled);
    }    
    
    public boolean isEligibleForEvent(Long sid, WampSubscription subscription, WampList payload, WampDict payloadKw)
    {
        return true;
    }
    
    public void updateOptions(WampSubscriptionOptions opts)
    {
        this.eventsEnabled = this.eventsEnabled || opts.eventsEnabled;
        this.metaTopics.addAll(opts.metaTopics);
    }
    
}

