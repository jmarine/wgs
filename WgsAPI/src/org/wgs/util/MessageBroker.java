package org.wgs.util;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.Message;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.MessageListener;
import javax.jms.Topic;
import javax.jms.TopicSession;
import javax.jms.TopicPublisher;
import javax.jms.TopicSubscriber;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.naming.InitialContext;

import com.sun.messaging.AdminConnectionFactory;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.jmsclient.runtime.BrokerInstance;
import com.sun.messaging.jmq.jmsclient.runtime.ClientRuntime;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsservice.BrokerEventListener;
import com.sun.messaging.jms.management.server.DestinationOperations;
import com.sun.messaging.jms.management.server.DestinationType;
import com.sun.messaging.jms.management.server.MQObjectName;
import org.wgs.wamp.WampDict;

import org.wgs.wamp.WampEncoding;
import org.wgs.wamp.WampList;
import org.wgs.wamp.WampObject;

import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampServices;
import org.wgs.wamp.WampSubscription;
import org.wgs.wamp.WampTopic;


public class MessageBroker
{
    private static final Logger logger = Logger.getLogger(MessageBroker.class.getName());
    
    private static boolean          brokerTested = false;
    private static boolean          brokerAvailable = false;
    private static BrokerInstance   brokerInstance = null;
    private static String           brokerId = "wgs-" + UUID.randomUUID().toString();
    
    private static TopicConnection  reusableTopicConnection = null;
    private static ConcurrentHashMap<WampTopic,TopicSubscriber> topicSubscriptions = new ConcurrentHashMap<WampTopic,TopicSubscriber>();
   
    
    public static void start(Properties serverConfig) throws Exception
    {
        String imqEnabled = serverConfig.getProperty("imq.enabled");
        if(imqEnabled != null && imqEnabled.equalsIgnoreCase("true")) {
            
            String imqHome = serverConfig.getProperty("imq.home");
            if(imqHome != null) {
                String instanceName = serverConfig.getProperty("imq.instancename", "wgs");
                System.out.println("Starting OpenMQ broker...");

                String[] args = { 
                    "-imqhome", serverConfig.getProperty("imq.home"), 
                    "-varhome", serverConfig.getProperty("imq.varhome"), 
                    "-name",    instanceName
                };

                ClientRuntime clientRuntime = ClientRuntime.getRuntime();
                brokerInstance = clientRuntime.createBrokerInstance();

                Properties props = brokerInstance.parseArgs(args);
                props.put("imq."+instanceName+".max_threads", serverConfig.getProperty("imq."+instanceName+".max_threads", "10000"));
                BrokerEventListener listener = new EmbeddedBrokerEventListener();
                brokerInstance.init(props, listener);
                brokerInstance.start();
            }

            com.sun.messaging.TopicConnectionFactory tcf = null;
            tcf = new com.sun.messaging.TopicConnectionFactory();
            tcf.setProperty(ConnectionConfiguration.imqAddressList, serverConfig.getProperty("imq.tcf.imqAddressList", "mq://localhost/direct"));
        
            InitialContext jndi = new InitialContext();
            jndi.bind("jms/TopicConnectionFactory", tcf);
        }
    }
    

    public static void stop() 
    {
        if(reusableTopicConnection != null) {
            try {
                //reusableTopicConnection.stop();
                reusableTopicConnection.close();
            } catch(Exception ex) { }
        }
        
        if(brokerInstance != null) {
            System.out.println("Stoping OpenMQ broker...");
            brokerInstance.stop();
            brokerInstance.shutdown();
        }
    }
    
    
    public synchronized static boolean isJmsBrokerAvailable()
    {
        if(!brokerTested) {
            try {
                TopicConnection con = getTopicConnectionFromPool();
                releaseTopicConnectionToPool(con);
                brokerAvailable = true;
            } catch(Exception ex) {
                brokerAvailable = false;
            } finally {
                brokerTested = true;
            }
        }
        return brokerAvailable;
    }
    
    
    private synchronized static TopicConnection getTopicConnectionFromPool() throws Exception
    {
        if(reusableTopicConnection == null) {
            InitialContext jndi = new InitialContext();
            TopicConnectionFactory tcf = (TopicConnectionFactory)jndi.lookup("jms/TopicConnectionFactory");
            reusableTopicConnection = tcf.createTopicConnection();
        }
        return reusableTopicConnection;
    }
    
    private static void releaseTopicConnectionToPool(TopicConnection topicConnection)
    {
        // topicConnection.close();   // don't close: it's now reused by all publishers/subscribers
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
    

    private static String normalizeTopicName(String topicName) 
    {
        int index = topicName.indexOf("://");
        if(index != -1) topicName = topicName.substring(index+3);

        topicName = topicName.replace(":", ".");
        topicName = topicName.replace("/", ".");        
        topicName = topicName.replace("#", ".");
        topicName = topicName.replace("-", "_");
        return topicName;
    }

    
    public static void subscribeMessageListener(WampTopic wampTopic, long sinceTime, long sinceN) throws Exception 
    {
        synchronized(wampTopic) {
            if(isJmsBrokerAvailable() && !topicSubscriptions.containsKey(wampTopic)) {
                String topicName = wampTopic.getURI();
                TopicConnection connection = getTopicConnectionFromPool();

                TopicSession subSession = connection.createTopicSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE);
                Topic jmsTopic = subSession.createTopic(normalizeTopicName(topicName));

                String selector = "";
                if(sinceTime > 0L) selector = "JMSTimestamp >= " + sinceTime;
                if(sinceN > 0L) {
                    if(selector.length() > 0) selector += " and ";
                    selector = "id >= " + sinceN;
                }

                synchronized(connection) {
                    connection.stop();
                    TopicSubscriber subscriber = subSession.createSubscriber(jmsTopic, selector, false);
                    subscriber.setMessageListener(new BrokerMessageListener());
                    connection.start();
                    topicSubscriptions.put(wampTopic, subscriber);
                }
                
                releaseTopicConnectionToPool(connection);

                System.out.println("Subscribed to " + topicName);
            }
        }
    }
    
    
    public static void unsubscribeMessageListener(WampTopic topic) throws Exception 
    {
        if(isJmsBrokerAvailable()) {
            TopicSubscriber subscriber = topicSubscriptions.remove(topic);
            subscriber.close();
        }
    }
    
    
    public static void publish(Long id, WampTopic wampTopic, WampList payload, WampDict payloadKw, String metaTopic, Set<Long> eligible, Set<Long> exclude, Long publisherId) throws Exception
    {
        broadcastClusterEventToLocalNodeClients(id, wampTopic,metaTopic,eligible,exclude,publisherId, payload, payloadKw);  
        
        if(isJmsBrokerAvailable()) {
            String topicName = wampTopic.getURI();
            TopicConnection connection = getTopicConnectionFromPool();

            TopicSession pubSession = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

            Topic topic = pubSession.createTopic(normalizeTopicName(topicName));
            TopicPublisher publisher = pubSession.createPublisher(topic);

            // send a message to the queue in the normal way
            WampList event = new WampList();
            event.add(payload);
            event.add(payloadKw);
            String eventPayLoad = (event != null)? (String)WampObject.getSerializer(WampEncoding.JSon).serialize(event) : null;
            TextMessage msg = pubSession.createTextMessage(eventPayLoad);
            
            msg.setLongProperty("id", id);
            if(publisherId != null) msg.setLongProperty("publisherId", publisherId);

            msg.setStringProperty("topic", topicName);
            if(metaTopic != null)   msg.setStringProperty("metaTopic", metaTopic);
            if(eligible != null)    msg.setStringProperty("eligible", serializeSessionIDs(eligible));
            if(exclude != null)     msg.setStringProperty("exclude", serializeSessionIDs(exclude));

            msg.setStringProperty("excludeBroker", brokerId);
            publisher.send(msg);

            publisher.close();
            pubSession.close();
            
            releaseTopicConnectionToPool(connection);
            //System.out.println ("Message sent to topic " + topicName + ": " + eventPayload);
        }
    }

    
    public static void publishMetaEvent(Long publicationId, WampTopic topic, String metatopic, Object metaevent, Long toClient) throws Exception
    {
        HashSet<Long> eligible = new HashSet<Long>();
        if(toClient != null) eligible.add(toClient);
        WampList payload = new WampList();
        payload.add(metaevent);
        publish(publicationId, topic, payload, null, metatopic, eligible, null, null);
    } 
    
    
    private static void broadcastClusterEventToLocalNodeClients(Long publicationId, WampTopic topic, String metaTopic, Set<Long> eligible, Set<Long> excluded, Long publisherId, WampList payload, WampDict payloadKw) throws Exception 
    {
        if(metaTopic == null) {
            // EVENT data
            WampProtocol.sendEvents(publicationId, topic, eligible, excluded, publisherId, (WampList)payload, payloadKw);
        } else {
            // METAEVENT data (WAMP v2)
            Object metaevent = payload.get(0);
            WampProtocol.sendMetaEvents(publicationId, topic, metaTopic, eligible, metaevent);
        }
    }       
    
    
    private static String serializeSessionIDs(Set<Long> ids) 
    {
        String str = ids.toString();
        return str.substring(1, str.length()-1);
    }
    
    
    private static HashSet<Long> parseSessionIDs(String ids) 
    {
        HashSet<Long> retval = null;
        if(ids != null) {
            retval = new HashSet<Long>();
            StringTokenizer stk = new StringTokenizer(ids, "," , false);
            while(stk.hasMoreTokens()) {
                Long id = Long.parseLong(stk.nextToken());
                retval.add(id);
            }
        }
        return retval;
    }     
    
    
    private static class BrokerMessageListener implements MessageListener 
    {
        @Override
        public void onMessage(Message receivedMessageFromBroker) {
            try {
                //System.out.println ("Received message from broker.");
                String excludeBroker = receivedMessageFromBroker.getStringProperty("excludeBroker");
                if(excludeBroker != null && excludeBroker.equals(brokerId)) return;
                
                String topicName = receivedMessageFromBroker.getStringProperty("topic");
                String metaTopic = receivedMessageFromBroker.getStringProperty("metaTopic");
                Long publisherId = receivedMessageFromBroker.propertyExists("publisherId") ? receivedMessageFromBroker.getLongProperty("publisherId") : null;

                String eventData = ((TextMessage)receivedMessageFromBroker).getText();
                //System.out.println ("Received message from topic " + topicName + ": " + eventData);
                
                WampList event = (eventData!=null)? (WampList)WampObject.getSerializer(WampEncoding.JSon).deserialize(eventData) : null;
                WampList payload = (WampList)event.get(0);  // EVENT.payload|list or METAEVENT.MetaEvent|any
                WampDict payloadKw = (WampDict)event.get(1);
                Set<Long> eligible = receivedMessageFromBroker.propertyExists("eligible")? parseSessionIDs(receivedMessageFromBroker.getStringProperty("eligible")) : null;
                Set<Long> exclude  = receivedMessageFromBroker.propertyExists("exclude")?  parseSessionIDs(receivedMessageFromBroker.getStringProperty("exclude"))  : null;

                for(WampSubscription subscription : WampServices.getTopic(topicName).getSubscriptions()) {
                    for(WampTopic topic : subscription.getTopics()) {
                        broadcastClusterEventToLocalNodeClients(subscription.getId(), topic, metaTopic, eligible, exclude, publisherId, payload, payloadKw);
                    }
                }
                
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error receiving message from broker", ex);
            }
        }
        
        
    }
    
}




class EmbeddedBrokerEventListener implements BrokerEventListener 
{
    @Override
    public void brokerEvent(BrokerEvent brokerEvent) 
    {
        System.out.println ("Received broker event: "+brokerEvent);
    }

    @Override
    public boolean exitRequested(BrokerEvent event, Throwable thr) 
    {
        System.out.println ("Broker is requesting a shutdown because of: "+event+" with "+thr);
        // return true to allow the broker to shutdown
        return true;
    }

}


