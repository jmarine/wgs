package org.wgs.wamp.api;

import org.wgs.wamp.types.WampList;
import org.wgs.wamp.annotation.WampRPC;
import org.wgs.wamp.annotation.WampModuleName;
import java.util.Collection;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.topic.Broker;
import org.wgs.wamp.WampSocket;


@WampModuleName("wamp")
public class WampAPI extends WampModule 
{
    public WampAPI(WampApplication app)
    {
        super(app);
    }

    //TODO:
    
    //wamp.reflection.topic.list
    //wamp.reflection.procedure.list
    //wamp.reflection.error.list
    
    //wamp.reflection.topic.describe
    //wamp.reflection.procedure.describe
    //wamp.reflection.error.describe

    
    @WampRPC(name=".broker.subscriber.list")
    public Collection<Long> getSubscribedSessions(WampSocket socket, Long subscriptionId) throws Exception
    {
        return Broker.getSubscriptionById(subscriptionId).getSessionIds();
    }    
    
    @WampRPC(name=".topic.history.last")
    public WampList getLastTopicEvents(String topicName, int limit)
    {
        return null;
    }
    
    @WampRPC(name=".topic.history.since")
    public WampList getTopicEventsSinceTimestamp(String topicName, Long timestampInMillis)
    {
        return null;
    }
    
    @WampRPC(name=".topic.history.after")
    public WampList getTopicEventsAfterID(String topicName, Long id)
    {
        return null;
    }    
    

}
