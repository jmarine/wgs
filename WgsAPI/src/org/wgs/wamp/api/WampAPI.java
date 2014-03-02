package org.wgs.wamp.api;

import java.util.Collection;
import org.wgs.util.Storage;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.annotation.WampRPC;
import org.wgs.wamp.annotation.WampModuleName;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampTopic;
import org.wgs.wamp.types.WampList;


@WampModuleName("wamp")
public class WampAPI extends WampModule 
{
    public WampAPI(WampApplication app)
    {
        super(app);
    }

    //TODO:
    
    //wamp.reflection.procedure.list
    //wamp.reflection.error.list
    
    //wamp.reflection.topic.describe
    //wamp.reflection.procedure.describe
    //wamp.reflection.error.describe
    
    @WampRPC(name="reflection.procedure.list")
    public WampList getProcedureList() throws Exception
    {
        return this.getWampApplication().getAllRpcNames();
    }
    
    @WampRPC(name="reflection.topic.list")
    public WampList getTopicList() throws Exception
    {
        WampList names = new WampList();
        for(WampTopic topic : Storage.findEntities(WampTopic.class, "wgs.findAllTopics")) {
            names.add(topic.getTopicName());
        }
        return names;
    }

    
    @WampRPC(name="broker.subscriber.list")
    public WampList getSubscribedSessions(WampSocket socket, Long subscriptionId) throws Exception
    {
        WampList retval = new WampList();
        WampSubscription subscription = WampBroker.getSubscriptionById(subscriptionId);
        if(subscription != null) {
            for(Long sid : subscription.getSessionIds()) {
                retval.add(sid);
            }
        }
        return retval;
    }    
    
    
    @WampRPC(name="topic.history.last")
    public WampList getLastTopicEvents(String topicName, int limit)
    {
        return null;
    }
    
    @WampRPC(name="topic.history.since")
    public WampList getTopicEventsSinceTimestamp(String topicName, Long timestampInMillis)
    {
        return null;
    }
    
    @WampRPC(name="topic.history.after")
    public WampList getTopicEventsAfterID(String topicName, Long id)
    {
        return null;
    }    
    

}
