package org.wgs.util;

import com.sun.messaging.AdminConnectionFactory;
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
import com.sun.messaging.jms.management.server.DestinationOperations;
import com.sun.messaging.jms.management.server.DestinationType;
import com.sun.messaging.jms.management.server.MQObjectName;
import java.util.Set;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;


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
    
    public static void stopEmbeddedBroker() 
    {
        if(brokerInstance != null) {
            brokerInstance.stop();
            brokerInstance.shutdown();
        }
    }
    
    
    public static void destroyTopic(String topicName) throws Exception
    {
        AdminConnectionFactory acf = new AdminConnectionFactory();
        JMXConnector jmxc = acf.createConnection("admin", "admin");
        MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

        ObjectName destMgrMonitorName = new ObjectName(MQObjectName.DESTINATION_MANAGER_CONFIG_MBEAN_NAME);

        Object opParams[] = { DestinationType.TOPIC, normalizeTopicName(topicName) };
        String opSig[] = { String.class.getName(), String.class.getName() };

        mbsc.invoke(destMgrMonitorName, DestinationOperations.DESTROY, opParams, opSig);
        jmxc.close();
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
        
        System.out.println("Subscribed to " + topicName);
        return connection;
    }
    
    public static void publish(long id, String topicName, String eventPayload, String metaTopic, Set eligible, Set exclude, String publisherId) throws Exception
    {
        TopicConnection connection = getTopicConnection();

        TopicSession pubSession = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

        Topic topic = pubSession.createTopic(normalizeTopicName(topicName));
        TopicPublisher publisher = pubSession.createPublisher(topic);

        // send a message to the queue in the normal way
        TextMessage msg = pubSession.createTextMessage(eventPayload);
        msg.setStringProperty("topic", topicName);
        if(id != 0L)            msg.setLongProperty("id", id);
        if(publisherId != null) msg.setStringProperty("publisherId", publisherId);
        if(metaTopic != null)   msg.setStringProperty("metaTopic", metaTopic);
        if(eligible != null) {
            String str = eligible.toString();
            msg.setStringProperty("eligible", str.substring(1, str.length()-1));
        }
        if(exclude != null) {
            String str = exclude.toString();
            msg.setStringProperty("exclude", str.substring(1, str.length()-1));
        }

        publisher.send(msg);
        publisher.close();
        pubSession.close();
        connection.close();
        
        System.out.println ("Message sent to topic " + topicName + ": " + eventPayload);
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
