package org.wgs.wamp;

import java.util.ArrayList;
import java.util.List;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;


public class WampSubscriptionOptions 
{

    public  enum MatchEnum { exact, prefix, wildcard };

    private MatchEnum    matchType;
    private List<String> metaEvents;
    private boolean      eventsEnabled;
    
    /*
    private boolean  eventIdRequested;
    private boolean  publisherIdRequested;
    private int      numberEventsToSendOnSubscription;
    private long     sinceTimeEventsToSendOnSubscription;
    */

    public WampSubscriptionOptions(JsonNode node) {
        this.matchType = MatchEnum.exact;
        this.metaEvents = new ArrayList<String>();
        this.eventsEnabled = true;
        
        if(node != null) {
            if(node.has("MATCH")) {
                setMatchType(MatchEnum.valueOf(node.get("MATCH").asText()));
            }     
            
            if(node.has("METAEVENTS")) {
                setMetaEvents(node.get("METAEVENTS"));
            }

            if(node.has("EVENTS")) {
                setEventsEnabled(node.get("EVENTS").asBoolean(true));
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
    public List<String> getMetaEvents() {
        return metaEvents;
    }

    /**
     * @param metaEvents the metaEvents to set
     */
    public void setMetaEvents(List<String> metaEvents) {
        this.metaEvents = metaEvents;
    }

    /**
     * @param arrayNode the JSON array node with the text elements to set
     */
    public void setMetaEvents(JsonNode arrayNode) {
        this.metaEvents = new ArrayList<String>();
        if(arrayNode != null) {
            for(int i = 0; i < arrayNode.size(); i++) {
                this.metaEvents.add(arrayNode.get(i).asText());
            }
        }
    }    
    
    public boolean hasMetaEventsEnabled()
    {
        return (this.metaEvents.size() > 0);
    }
    
    
    public void setEventsEnabled(boolean eventsEnabled) {
        this.eventsEnabled = eventsEnabled;
    }
    
    public boolean hasEventsEnabled()
    {
        return (this.eventsEnabled);
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

