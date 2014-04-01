package org.wgs.wamp.jms;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Topic;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampTopic;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampObject;


public class WampTopicSubscriber extends WampModule implements javax.jms.TopicSubscriber
{
    private WampTopicSession session;
    
    private boolean noLocal;
    
    private Topic topic;
    
    private String messageSelector;
    
    private MessageListener messageListener;
    
    private WampSubscription wampSubscription;

    
    public WampTopicSubscriber(WampTopicSession session, Topic topic, String messageSelector, boolean noLocal)
    {
        super(((WampTopicConnection)session.getTopicConnection()).getWampApplication());
        this.session = session;
        this.topic = topic;
        this.messageSelector = messageSelector;
        this.noLocal = noLocal;
    }
    
    @Override
    public Topic getTopic() throws JMSException {
        return topic;
    }
    
    @Override
    public void onEvent(WampSocket serverSocket, WampList request) throws Exception     
    {
        if(messageListener != null) {
            Long subscriptionId = request.getLong(1);
            if(subscriptionId.equals(wampSubscription.getId())) {
                Long publicationId = request.getLong(2);
                WampDict details = (WampDict)request.get(3);
                WampMessage msg = new WampMessage(publicationId, details);
                WampList list = new WampList();
                if(request.size() > 4) list.add(request.get(4));
                if(request.size() > 5) list.add(request.get(5));
                msg.setText(WampObject.getSerializer(WampEncoding.JSon).serialize(list).toString());
                messageListener.onMessage(msg);
            }
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
        //TODO: send [UNSUBSCRIBE, WampProtocol.newId(), wampSubscription.getId()]
    }
    
}
