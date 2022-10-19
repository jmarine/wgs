package org.wgs.wamp.jms;

import java.io.Serializable;
import jakarta.jms.BytesMessage;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.StreamMessage;
import jakarta.jms.TemporaryQueue;
import jakarta.jms.TemporaryTopic;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import jakarta.jms.TopicConnection;
import jakarta.jms.TopicPublisher;
import jakarta.jms.TopicSubscriber;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.topic.WampTopic;
import org.wgs.wamp.topic.WampTopicOptions;


public class WampTopicSession implements jakarta.jms.TopicSession
{
    private WampTopicConnection con; 
    private int acknowledgeMode;
    
    
    public WampTopicSession(WampTopicConnection con, boolean transacted, int acknowledgeMode) {
        this.con = con;
        this.acknowledgeMode = acknowledgeMode;
    }
    
    public TopicConnection getTopicConnection()
    {
        return this.con;
    }
    

    @Override
    public Topic createTopic(String topicName) throws JMSException {
        return new WampTopic(topicName, null);
    }

    @Override
    public TopicSubscriber createSubscriber(Topic topic) throws JMSException {
        return createSubscriber(topic, null, false);
    }

    @Override
    public TopicSubscriber createSubscriber(Topic topic, String messageSelector, boolean noLocal) throws JMSException {
        WampTopicSubscriber subscriber = new WampTopicSubscriber(this, topic, messageSelector, noLocal);
        con.getWampClient().getWampApplication().registerWampModule(subscriber);
        con.requestSubscription(subscriber);
        return subscriber;
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String string) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String string, String string1, boolean bln) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public TopicPublisher createPublisher(Topic topic) throws JMSException {
        return new WampTopicPublisher(this, topic);
    }

    @Override
    public TemporaryTopic createTemporaryTopic() throws JMSException {
        String uri = "wamp.topic.temp." + WampProtocol.newGlobalScopeId();
        WampTopicOptions topicOptions = new WampTopicOptions();
        topicOptions.setTemporary(true);
        WampTopic topic = new WampTopic(uri, topicOptions);
        return topic;
    }

    @Override
    public void unsubscribe(String string) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public BytesMessage createBytesMessage() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MapMessage createMapMessage() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Message createMessage() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ObjectMessage createObjectMessage() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable srlzbl) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public StreamMessage createStreamMessage() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public TextMessage createTextMessage() throws JMSException {
        return new WampMessage();
    }

    @Override
    public TextMessage createTextMessage(String text) throws JMSException {
        WampMessage msg = new WampMessage();
        if(text != null) msg.setText(text);
        return msg;
    }

    @Override
    public boolean getTransacted() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getAcknowledgeMode() throws JMSException {
        return acknowledgeMode;
    }

    @Override
    public void commit() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void rollback() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void close() throws JMSException {
        con = null;
    }

    @Override
    public void recover() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MessageListener getMessageListener() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setMessageListener(MessageListener ml) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void run() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MessageProducer createProducer(Destination dstntn) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MessageConsumer createConsumer(Destination dstntn) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MessageConsumer createConsumer(Destination dstntn, String string) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MessageConsumer createConsumer(Destination dstntn, String string, boolean bln) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic, String string) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic, String string, String string1) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Queue createQueue(String string) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MessageConsumer createDurableConsumer(Topic topic, String string) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MessageConsumer createDurableConsumer(Topic topic, String string, String string1, boolean bln) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String string) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String string, String string1) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public QueueBrowser createBrowser(Queue queue) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public QueueBrowser createBrowser(Queue queue, String string) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public TemporaryQueue createTemporaryQueue() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
