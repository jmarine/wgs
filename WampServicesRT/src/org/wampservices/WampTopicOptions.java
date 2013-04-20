package org.wampservices;


public class WampTopicOptions 
{
    private boolean eventPersistence;
    private boolean deletionOfEventsOnTopicRemoval;
    private int     maxHistoricEvents;

    
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
