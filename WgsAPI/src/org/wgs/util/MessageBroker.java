package org.wgs.util;

import java.util.Properties;

import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.MessageListener;
import javax.jms.Topic;
import javax.jms.TopicSession;
import javax.jms.TopicPublisher;
import javax.jms.TopicSubscriber;
import javax.naming.InitialContext;

import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.jmsclient.runtime.BrokerInstance;
import com.sun.messaging.jmq.jmsclient.runtime.ClientRuntime;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsservice.BrokerEventListener;


public class MessageBroker 
{
    private static BrokerInstance brokerInstance = null;


    public static TopicConnectionFactory startEmbeddedBroker(Properties serverConfig) throws Exception
    {
        String[] args = { 
            "-imqhome", serverConfig.getProperty("imq.home"), 
            "-varhome", serverConfig.getProperty("imq.varhome"), 
            "-name",    serverConfig.getProperty("imq.instancename")
        };

        ClientRuntime clientRuntime = ClientRuntime.getRuntime();
        brokerInstance = clientRuntime.createBrokerInstance();

        Properties props = brokerInstance.parseArgs(args);
        BrokerEventListener listener = new EmbeddedBrokerEventListener();
        brokerInstance.init(props, listener);
        brokerInstance.start();

        com.sun.messaging.TopicConnectionFactory tcf = new com.sun.messaging.TopicConnectionFactory();
        tcf.setProperty(ConnectionConfiguration.imqAddressList, serverConfig.getProperty("imq.tcf.imqAddressList", "mq://localhost/direct"));
        return tcf;
    }
    
    public static TopicConnection getTopicConnection() throws Exception
    {
        InitialContext jndi = new InitialContext();
        TopicConnectionFactory tcf = (TopicConnectionFactory)jndi.lookup("jms/TopicConnectionFactory");
        return tcf.createTopicConnection();
    }
    
    private static String normalizeTopicName(String topicName) 
    {
        int index = topicName.indexOf("://");
        if(index != -1) topicName = topicName.substring(index+3);

        topicName = topicName.replace(":", ".");
        topicName = topicName.replace("#", ".");
        topicName = topicName.replace("-", "_");
        return topicName;
    }
    
    public static Topic createTopic(String topicName) throws Exception
    {
        TopicConnection connection = getTopicConnection();
        TopicSession session = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(normalizeTopicName(topicName));
        session.close();
        connection.close();
        return topic;
    }
    
    public static TopicConnection subscribeMessageListener(String topicName, long sinceTime, long sinceN, MessageListener listener) throws Exception 
    {
        TopicConnection connection = getTopicConnection();
        TopicSession subSession = connection.createTopicSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE);
        Topic jmsTopic = subSession.createTopic(normalizeTopicName(topicName));
        
        String selector = "";
        if(sinceTime > 0L) selector = "JMSTimestamp >= " + sinceTime;
        if(sinceN > 0L) {
            if(selector.length() > 0) selector += " and ";
            selector = "id >= " + sinceN;
        }
        
        TopicSubscriber subscriber = subSession.createSubscriber(jmsTopic, selector, false);
        subscriber.setMessageListener(listener);
        connection.start();
        return connection;
    }
    
    public static void publish(String topicName, long id, String eventPayload, String eligible, String exclude, String publisherId) throws Exception
    {
        TopicConnection connection = getTopicConnection();

        TopicSession pubSession = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

        Topic topic = pubSession.createTopic(normalizeTopicName(topicName));
        TopicPublisher publisher = pubSession.createPublisher(topic);

        // send a message to the queue in the normal way
        TextMessage msg = pubSession.createTextMessage(eventPayload);
        msg.setStringProperty("topic", topicName);
        if(id != 0L)            msg.setLongProperty("id", id);
        if(eligible != null)    msg.setStringProperty("eligible", eligible);
        if(exclude != null)     msg.setStringProperty("exclude", exclude);
        if(publisherId != null) msg.setStringProperty("publisherId", publisherId);
        
        publisher.send(msg);
        publisher.close();
        pubSession.close();
        
        System.out.println ("Message sent to broker " + eventPayload);
    }

    
    public static void stopEmbeddedBroker() 
    {
        if(brokerInstance != null) {
            brokerInstance.stop();
            brokerInstance.shutdown();
        }
    }
    

}


class EmbeddedBrokerEventListener implements BrokerEventListener 
{
    public void brokerEvent(BrokerEvent brokerEvent) 
    {
        System.out.println ("Received broker event: "+brokerEvent);
    }

    public boolean exitRequested(BrokerEvent event, Throwable thr) 
    {
        System.out.println ("Broker is requesting a shutdown because of: "+event+" with "+thr);
        // return true to allow the broker to shutdown
        return true;
    }

}
