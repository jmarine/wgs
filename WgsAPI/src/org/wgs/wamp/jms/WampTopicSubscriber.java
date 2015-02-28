package org.wgs.wamp.jms;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Topic;
import org.jdeferred.DoneCallback;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.client.WampClient;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampSubscriptionOptions;
import org.wgs.wamp.topic.WampTopic;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampObject;


public class WampTopicSubscriber extends WampModule implements javax.jms.TopicSubscriber
{
    private Long subscriptionId;
    
    private boolean subscribed;
    
    private WampClient client;
    
    private WampTopicSession session;
    
    private boolean noLocal;
    
    private Topic topic;
    
    private String messageSelector;
    
    private MessageListener messageListener;
    
    
    public WampTopicSubscriber(WampTopicSession session, Topic topic, String messageSelector, boolean noLocal) throws JMSException
    {
        super(((WampTopicConnection)session.getTopicConnection()).getWampClient().getWampApplication());
        this.session = session;
        this.topic = topic;
        this.messageSelector = messageSelector;
        this.noLocal = noLocal;
        this.client = ((WampTopicConnection)session.getTopicConnection()).getWampClient();
    }
    
    public void start() throws JMSException 
    {
        if(client.isOpen()) {
            this.client.subscribe(topic.getTopicName(), null).done(new DoneCallback<Long>() {
                @Override
                public void onDone(Long id) {
                    WampTopicSubscriber.this.setSubscriptionId(id);
                }
            });
            subscribed = true;
        }
    }
    
    public void stop() throws JMSException
    {
        if(subscribed) {
            WampSubscriptionOptions options = new WampSubscriptionOptions(null);
            if(client.isOpen()) this.client.unsubscribe(topic.getTopicName(), options);
            subscribed = false;
        }
    }

    public void setSubscriptionId(Long subscriptionId) 
    {
        this.subscriptionId = subscriptionId;
    }
    
    public Long getSubscriptionId() 
    {
        return subscriptionId;
    }
    
    public void setSubscribed(boolean subscribed)
    {
        this.subscribed = subscribed;
    }
    
    public boolean getSubscribed()
    {
        return this.subscribed;
    }
    
    
    @Override
    public Topic getTopic() throws JMSException {
        return topic;
    }
    
    @Override
    public void onEvent(WampSocket serverSocket, Long subscriptionId, Long publicationId, WampDict details, WampList payload, WampDict payloadKw) throws Exception
    {
        if( messageListener != null
                && subscriptionId.equals(this.subscriptionId)
                && !(noLocal && details.getLong("publisher").equals(((WampTopicConnection)session.getTopicConnection()).getWampClient().getWampSocket().getSessionId())) ) {
            WampMessage msg = new WampMessage(publicationId, details, payload, payloadKw);
            messageListener.onMessage(msg);
        }
    }            
    

    @Override
    public boolean getNoLocal() throws JMSException {
        return noLocal;
    }

    @Override
    public String getMessageSelector() throws JMSException {
        return messageSelector;
    }

    @Override
    public MessageListener getMessageListener() throws JMSException {
        return messageListener;
    }

    @Override
    public void setMessageListener(MessageListener ml) throws JMSException {
        this.messageListener = ml;
    }

    @Override
    public Message receive() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Message receive(long timeout) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Message receiveNoWait() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void close() throws JMSException {
        client.unsubscribe(topic.getTopicName(), null);
    }
    
}
