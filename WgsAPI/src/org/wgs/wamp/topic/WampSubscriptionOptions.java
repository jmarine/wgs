package org.wgs.wamp.topic;

import java.util.HashSet;
import org.wgs.wamp.types.WampMatchType;
import org.wgs.wamp.types.WampDict;
import org.wgs.wamp.types.WampList;



public class WampSubscriptionOptions 
{

    private WampMatchType    matchType;
    private boolean      eventsEnabled;
    private HashSet<String> metaTopics;
    

    public WampSubscriptionOptions(WampDict node) 
    {
        this.matchType = WampMatchType.exact;
        this.eventsEnabled = true;
        this.metaTopics = new HashSet<String>();
        
        if(node != null) {
            if(node.has("match")) {
                setMatchType(WampMatchType.valueOf(node.getText("match")));
            }     
            
            if(node.has("metatopics")) {
                setMetaTopics((WampList)node.get("metatopics"));
            }

            if(node.has("metaonly")) {
                setEventsEnabled(!node.getBoolean("metaonly").booleanValue());
            }     

        }
    }
    
    /**
     * @return the matchType
     */
    public WampMatchType getMatchType() {
        return matchType;
    }

    /**
     * @param matchType the matchType to set
     */
    public void setMatchType(WampMatchType matchType) {
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
                this.metaTopics.add(metaTopics.getText(i));
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

