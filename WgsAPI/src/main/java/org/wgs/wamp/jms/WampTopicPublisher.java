package org.wgs.wamp.jms;

import java.util.Enumeration;
import jakarta.jms.CompletionListener;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Topic;
import org.wgs.wamp.client.WampClient;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.topic.WampPublishOptions;
import org.wgs.wamp.topic.WampTopic;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


public class WampTopicPublisher implements jakarta.jms.TopicPublisher
{
    private Topic topic;
    private WampTopicSession session;
    
    private int  deliveryMode; 
    private int  priority;
    private long timeToLive;
    private long deliveryDelay;
    
    
    public WampTopicPublisher(WampTopicSession session, Topic topic)
    {
        this.session = session;
        this.topic = topic;
    }

    @Override
    public Topic getTopic() throws JMSException {
        return topic;
    }

    @Override
    public void publish(Message msg) throws JMSException {
        send(msg);
    }

    @Override
    public void publish(Message msg, int deliveryMode, int priority, long timeToLive) throws JMSException {
        send(msg, deliveryMode, priority, timeToLive);
    }

    @Override
    public void publish(Topic topic, Message msg) throws JMSException {
        send(topic, msg);
    }

    @Override
    public void publish(Topic topic, Message msg, int deliveryMode, int priority, long timeToLive) throws JMSException {
        send(topic, msg, deliveryMode, priority, timeToLive);
    }

    @Override
    public void setDisableMessageID(boolean bln) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean getDisableMessageID() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setDisableMessageTimestamp(boolean bln) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean getDisableMessageTimestamp() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setDeliveryMode(int deliveryMode) throws JMSException {
        this.deliveryMode = deliveryMode;
    }

    @Override
    public int getDeliveryMode() throws JMSException {
        return deliveryMode;
    }

    @Override
    public void setPriority(int priority) throws JMSException {
        this.priority = priority;
    }

    @Override
    public int getPriority() throws JMSException {
        return this.priority;
    }

    @Override
    public void setTimeToLive(long timeToLive) throws JMSException {
        this.timeToLive = timeToLive;
    }

    @Override
    public long getTimeToLive() throws JMSException {
        return this.timeToLive;
    }

    @Override
    public void setDeliveryDelay(long deliveryDelay) throws JMSException {
        this.deliveryDelay = deliveryDelay;
    }

    @Override
    public long getDeliveryDelay() throws JMSException {
        return this.deliveryDelay;
    }

    @Override
    public Destination getDestination() throws JMSException {
        return topic;
    }

    @Override
    public void close() throws JMSException {
        session = null;
    }

    @Override
    public void send(Message msg) throws JMSException {
        send(topic, msg, getDeliveryMode(), getPriority(), getTimeToLive());
    }

    @Override
    public void send(Message msg, int deliveryMode, int priority, long timeToLive) throws JMSException {
        send(topic, msg, deliveryMode, priority, timeToLive);
    }

    @Override
    public void send(Destination destination, Message msg) throws JMSException {
        send(destination, msg, getDeliveryMode(), getPriority(), getTimeToLive());
    }

    @Override
    public void send(Destination destination, Message m, int deliveryMode, int priority, long timeToLive) throws JMSException {
        try {
            m.setJMSDestination(destination);
            WampTopic topic = (WampTopic)destination;
            WampPublishOptions options = new WampPublishOptions();
            options.setAck(true);
            options.setExcludeMe(false);
            options.setDiscloseMe(true);

            WampTopicConnection tc = (WampTopicConnection)session.getTopicConnection();
            WampList payload = m.getBody(WampList.class);
            WampDict payloadKw = m.getBody(WampDict.class);
            if(payloadKw == null) payloadKw = new WampDict();
            
            WampDict allOptions = options.toWampObject();
            Enumeration e = m.getPropertyNames();
            while(e.hasMoreElements()) {
                String propName = (String)e.nextElement();
                allOptions.put(propName, m.getObjectProperty(propName));
            }
            
            if(!tc.isOpen()) tc.connect();
            tc.getWampClient().publish(topic.getTopicName(), payload, payloadKw, allOptions);
        } catch(Exception ex) {
            throw new JMSException(ex.getMessage());
        }
    }

    @Override
    public void send(Message msg, CompletionListener cl) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void send(Message msg, int deliveryMode, int priority, long timeToLive, CompletionListener cl) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void send(Destination dstntn, Message msg, CompletionListener cl) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void send(Destination dstntn, Message msg, int deliveryMode, int priority, long timeToLive, CompletionListener cl) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
