package com.github.jmarine.wampservices;


public class WampSubscriptionOptions 
{
    private boolean publisherIdRequested;
    private int     numberEventsToSendOnSubscription;


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
