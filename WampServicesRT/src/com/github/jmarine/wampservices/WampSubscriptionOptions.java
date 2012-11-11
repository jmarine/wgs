package com.github.jmarine.wampservices;

import org.codehaus.jackson.JsonNode;


public class WampSubscriptionOptions 
{
    private boolean  eventsEnabled;
    private boolean  presenceEnabled;
    private boolean  metaEventsEnabled;
    private boolean  eventIdRequested;
    private boolean  publisherIdRequested;
    private int      numberEventsToSendOnSubscription;
    private long     sinceTimeEventsToSendOnSubscription;
    private JsonNode status;

    public WampSubscriptionOptions(JsonNode node) {
        this.eventsEnabled = true;
        // this.sinceTimeEventsToSendOnSubscription = System.currentTimeMillis();
        
        if(node != null) {
            if(node.has("events")) {
                setEventsEnabled(node.get("events").asBoolean(true));
            }     
            
            if(node.has("metaevents")) {
                setMetaEventsEnabled(node.get("metaevents").asBoolean(false));
            }          
            
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
            
            if(node.has("presence")) {
                setPresenceEnabled(node.get("presence").asBoolean(false));
            } 
            
            if(node.has("status")) {
                setStatus(node.get("status"));
            }
              
        }
    }

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

    public boolean isEventsEnabled() {
        return eventsEnabled;
    }

    public void setEventsEnabled(boolean eventsEnabled) {
        this.eventsEnabled = eventsEnabled;
    }

    public boolean isMetaEventsEnabled() {
        return metaEventsEnabled;
    }

    public void setMetaEventsEnabled(boolean metaEventsEnabled) {
        this.metaEventsEnabled = metaEventsEnabled;
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

    public boolean isPresenceEnabled() {
        return presenceEnabled;
    }

    public void setPresenceEnabled(boolean presenceEnabled) {
        this.presenceEnabled = presenceEnabled;
    }

    public JsonNode getStatus() {
        return status;
    }

    public void setStatus(JsonNode status) {
        this.status = status;
    }
    
}
