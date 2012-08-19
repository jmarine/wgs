package com.github.jmarine.wampservices;


public class WampSubscriptionOptions 
{
    private boolean publisherIdRevealed;
    private int     numberEventsToSendOnSubscription;


    public boolean isPublisherIdRevealed() {
        return publisherIdRevealed;
    }

    public void setPublisherIdRevealed(boolean publisherIdRevealed) {
        this.publisherIdRevealed = publisherIdRevealed;
    }
    

    public int getNumberEventsToSendOnSubscription() {
        return numberEventsToSendOnSubscription;
    }

    public void setNumberEventsToSendOnSubscription(int numberEventsToSendOnSubscription) {
        this.numberEventsToSendOnSubscription = numberEventsToSendOnSubscription;
    }
    
    
}
