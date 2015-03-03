package org.wgs.wamp.api;

import org.wgs.security.WampCRA;
import org.wgs.util.Storage;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.annotation.WampRegisterProcedure;
import org.wgs.wamp.annotation.WampModuleName;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampTopic;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


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
    
    @WampRegisterProcedure(name="reflection.procedure.list")
    public WampList getProcedureList(WampSocket socket) throws Exception
    {
        return this.getWampApplication().getAllRpcNames(socket.getRealm());
    }
    
    @WampRegisterProcedure(name="reflection.topic.list")
    public WampList getTopicList() throws Exception
    {
        WampList names = new WampList();
        for(WampTopic topic : Storage.findEntities(WampTopic.class, "wgs.findAllTopics")) {
            names.add(topic.getTopicName());
        }
        return names;
    }

    
    @WampRegisterProcedure(name="broker.subscriber.list")
    public WampList getSubscribedSessions(WampSocket socket, Long subscriptionId) throws Exception
    {
        WampList retval = new WampList();
        WampSubscription subscription = WampBroker.getSubscriptionById(subscriptionId);
        if(subscription != null) {
            for(Long sid : subscription.getSessionIds(socket.getRealm())) {
                retval.add(sid);
            }
        }
        return retval;
    }    
    
    
    @WampRegisterProcedure(name="topic.history.last")
    public WampList getLastTopicEvents(String topicName, int limit)
    {
        return null;
    }
    
    @WampRegisterProcedure(name="topic.history.since")
    public WampList getTopicEventsSinceTimestamp(String topicName, Long timestampInMillis)
    {
        return null;
    }
    
    @WampRegisterProcedure(name="topic.history.after")
    public WampList getTopicEventsAfterID(String topicName, Long id)
    {
        return null;
    }    
    

}
