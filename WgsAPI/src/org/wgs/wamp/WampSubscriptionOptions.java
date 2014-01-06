package org.wgs.wamp;

import java.util.HashSet;
import java.util.List;
import org.codehaus.jackson.JsonNode;


public class WampSubscriptionOptions 
{

    public  enum MatchEnum { exact, prefix, wildcard };

    private MatchEnum    matchType;
    private boolean      eventsEnabled;
    private HashSet<String> metaEvents;
    

    public WampSubscriptionOptions(WampDict node) 
    {
        this.matchType = MatchEnum.exact;
        this.eventsEnabled = true;
        this.metaEvents = new HashSet<String>();
        
        if(node != null) {
            if(node.has("match")) {
                setMatchType(MatchEnum.valueOf(node.get("match").asText()));
            }     
            
            if(node.has("metaevents")) {
                setMetaEvents((List)node.get("metaevents").getObject());
            }

            if(node.has("metaonly")) {
                setEventsEnabled(!node.get("metaonly").asBoolean().booleanValue());
            }     
            
            /*
            if(node.has("eventId")) {
                setEventIdRequested(node.get("eventId").asBoolean(false));
            }
            
            if(node.has("identifyEventPublisher")) {
                setPublisherIdRequested(node.get("identifyEventPublisher").asBoolean(false));
            }
            
            if(node.has("last")) {
                setNumberEventsToSendOnSubscription(node.get("last").asInt(0));
            }
            
            if(node.has("sinceTime")) {
                setSinceTimeEventsToSendOnSubscription(node.get("sinceTime").asLong(0));
            }            
            
            if(node.has("STATUS")) {
                setStatus(node.get("STATUS"));
            }            

            */
              
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
     * @return the metaEvents
     */
    public HashSet<String> getMetaEvents() {
        return metaEvents;
    }

    /**
     * @param metaEvents the metaEvents to set
     */
    public void setMetaEvents(List metaEvents) {
        this.metaEvents = new HashSet<String>();
        if(metaEvents != null) {
            for(int i = 0; i < metaEvents.size(); i++) {
                this.metaEvents.add((String)metaEvents.get(i));
            }
        }
    }

    /**
     * @param arrayNode the JSON array node with the text elements to set
     */
    public void setMetaEvents(JsonNode arrayNode) {
        this.metaEvents = new HashSet<String>();
        if(arrayNode != null) {
            for(int i = 0; i < arrayNode.size(); i++) {
                this.metaEvents.add(arrayNode.get(i).asText());
            }
        }
    }    

    public boolean hasMetaEvents()
    {
        return (metaEvents.size() > 0);
    }
        
    
    public boolean hasMetaEvent(String metatopic)
    {
        return (metaEvents.contains(metatopic));
    }
    
    
    public void setEventsEnabled(boolean eventsEnabled) {
        this.eventsEnabled = eventsEnabled;
    }
    
    public boolean hasEventsEnabled()
    {
        return (this.eventsEnabled);
    }    
    
    public boolean isEligibleForEvent(Long sid, WampSubscription subscription, WampObject event)
    {
        return true;
    }
    
    public void updateOptions(WampSubscriptionOptions opts)
    {
        this.eventsEnabled = this.eventsEnabled || opts.eventsEnabled;
        this.metaEvents.addAll(opts.metaEvents);
    }
    
/*
    public boolean isPublisherIdRequested() {
        return publisherIdRequested;
    }

    public void setPublisherIdRequested(boolean publisherIdRevealed) {
        this.publisherIdRequested = publisherIdRevealed;
    }
    

    public int getNumberEventsToSendOnSubscription() {
        return numberEventsToSendOnSubscription;
    }

    public void setNumberEventsToSendOnSubscription(int numberEventsToSendOnSubscription) {
        this.numberEventsToSendOnSubscription = numberEventsToSendOnSubscription;
    }


    public boolean isEventIdRequested() {
        return eventIdRequested;
    }

    public void setEventIdRequested(boolean eventIdRequested) {
        this.eventIdRequested = eventIdRequested;
    }

    public long getSinceTimeEventsToSendOnSubscription() {
        return sinceTimeEventsToSendOnSubscription;
    }

    public void setSinceTimeEventsToSendOnSubscription(long sinceTimeEventsToSendOnSubscription) {
        this.sinceTimeEventsToSendOnSubscription = sinceTimeEventsToSendOnSubscription;
    }

    
    public JsonNode getStatus() {
        return status;
    }

    public void setStatus(JsonNode status) {
        this.status = status;
    }

    */
    
}

