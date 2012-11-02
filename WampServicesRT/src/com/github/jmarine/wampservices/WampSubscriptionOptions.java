package com.github.jmarine.wampservices;

import org.codehaus.jackson.JsonNode;


public class WampSubscriptionOptions 
{
    private boolean publisherIdRequested;
    private int     numberEventsToSendOnSubscription;

    public WampSubscriptionOptions(JsonNode node) {
        if(node != null) {
            if(node.has("identifyEventPublisher")) {
                setPublisherIdRequested(node.get("identifyEventPublisher").asBoolean(false));
            }
            if(node.has("last")) {
                setNumberEventsToSendOnSubscription(node.get("last").asInt(0));
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
    
    
}
