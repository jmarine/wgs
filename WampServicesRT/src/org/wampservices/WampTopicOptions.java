package org.wampservices;


public class WampTopicOptions 
{
    private boolean eventPersistence;
    private boolean deletionOfEventsOnTopicRemoval;
    private int     maxHistoricEvents;
    private boolean publisherIdRevelationEnabled;


    public boolean isPublisherIdRevelationEnabled() {
        return publisherIdRevelationEnabled;
    }

    public void setPublisherIdRevelationEnabled(boolean enable) {
        this.publisherIdRevelationEnabled = enable;
    }

    
    public boolean hasEventPersistence() {
        return eventPersistence;
    }

    public void setEventPersistence(boolean eventPersistence) {
        this.eventPersistence = eventPersistence;
    }

    
    public boolean hasDeletionOfEventsOnTopicRemoval() {
        return deletionOfEventsOnTopicRemoval;
    }

    public void setDeletionOfEventsOnTopicRemoval(boolean deletionOfEventsOnTopicRemoval) {
        this.deletionOfEventsOnTopicRemoval = deletionOfEventsOnTopicRemoval;
    }

    
    public int getMaxHistoricEvents() {
        return maxHistoricEvents;
    }

    public void setMaxHistoricEvents(int maxHistoricEvents) {
        this.maxHistoricEvents = maxHistoricEvents;
    }

    
}
