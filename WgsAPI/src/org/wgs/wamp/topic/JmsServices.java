package org.wgs.wamp.topic;

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
import javax.naming.InitialContext;


import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampObject;



public class JmsServices
{
    private static final Logger logger = Logger.getLogger(JmsServices.class.getName());
    
    private static String           imqEnabled = null;
    private static boolean          brokerTested = false;
    private static boolean          brokerAvailable = false;
    private static String           brokerId = "wgs-" + UUID.randomUUID().toString();
    
    private static TopicConnection  reusableTopicConnection = null;
    private static ConcurrentHashMap<WampTopic,TopicSubscriber> topicSubscriptions = new ConcurrentHashMap<WampTopic,TopicSubscriber>();
   
    
    public static void start(Properties serverConfig) throws Exception
    {
        imqEnabled = serverConfig.getProperty("imq.enabled");
        if(imqEnabled != null && imqEnabled.equalsIgnoreCase("true")) {        
            EmbeddedOpenMQ.start(serverConfig);
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
        
        if(imqEnabled != null && imqEnabled.equalsIgnoreCase("true")) {        
            EmbeddedOpenMQ.stop();
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
            TopicConnectionFactory tcf = (TopicConnectionFactory)jndi.lookup("jms/CentralTopicConnectionFactory");
            reusableTopicConnection = tcf.createTopicConnection();
        }
        return reusableTopicConnection;
    }
    
    private static void releaseTopicConnectionToPool(TopicConnection topicConnection)
    {
        // topicConnection.close();   // don't close: it's now reused by all publishers/subscribers
    }
    
    

    

    public static String normalizeTopicName(String topicName) 
    {
        int index = topicName.indexOf("://");
        if(index != -1) topicName = topicName.substring(index+3);

        topicName = topicName.replace(":", ".");
        topicName = topicName.replace("/", ".");        
        topicName = topicName.replace("#", ".");
        topicName = topicName.replace("-", "_");
        return topicName;
    }

    
    static void subscribeMessageListener(WampTopic wampTopic) throws Exception 
    {
        synchronized(wampTopic) {
            if(isJmsBrokerAvailable() && !topicSubscriptions.containsKey(wampTopic)) {
                String topicName = wampTopic.getTopicName();
                TopicConnection connection = getTopicConnectionFromPool();

                TopicSession subSession = connection.createTopicSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE);
                Topic jmsTopic = subSession.createTopic(normalizeTopicName(topicName));

                String selector = "";
                /*
                if(sinceTime > 0L) selector = "JMSTimestamp >= " + sinceTime;
                if(sinceN >= 0L) {
                    if(selector.length() > 0) selector += " and ";
                    selector = "id >= " + sinceN;
                }
                */

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
    

    static void unsubscribeMessageListener(WampTopic topic)
    {
        if(isJmsBrokerAvailable()) {
            try {
                TopicSubscriber subscriber = topicSubscriptions.remove(topic);
                subscriber.close();
            } catch(Exception ex) { }
        }
    }
    
    
    static void publishEvent(String realm, Long id, WampTopic wampTopic, String metaTopic, WampList payload, WampDict payloadKw, Set<Long> eligible, Set<Long> exclude, Long publisherId) throws Exception
    {
        if(!isJmsBrokerAvailable()) {
            broadcastClusterEventToLocalNodeClients(realm, id, wampTopic,metaTopic,eligible,exclude,publisherId, payload, payloadKw);  
        } else {
            String topicName = wampTopic.getTopicName();
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
            if(realm != null)       msg.setStringProperty("realm", realm);
            if(metaTopic != null)   msg.setStringProperty("metaTopic", metaTopic);
            if(eligible != null)    msg.setStringProperty("eligible", serializeSessionIDs(eligible));
            if(exclude != null)     msg.setStringProperty("exclude", serializeSessionIDs(exclude));

            // msg.setStringProperty("excludeBroker", brokerId);
            
            publisher.send(msg);

            publisher.close();
            pubSession.close();
            
            releaseTopicConnectionToPool(connection);
            //System.out.println ("Message sent to topic " + topicName + ": " + eventPayload);
        }
    }

    
    static void publishMetaEvent(String realm, Long publicationId, WampTopic topic, String metatopic, WampDict metaEventDetails, Long toClient) throws Exception
    {
        if(metaEventDetails == null) metaEventDetails = new WampDict();
        metaEventDetails.put("metatopic", metatopic);
        
        HashSet<Long> eligible = null;
        if(toClient != null) {
            eligible = new HashSet<Long>();            
            eligible.add(toClient);
        }
        
        publishEvent(realm, publicationId, topic, metatopic, null, metaEventDetails, eligible, null, null);
    } 
    
    
    private static void broadcastClusterEventToLocalNodeClients(String realm, Long publicationId, WampTopic topic, String metaTopic, Set<Long> eligible, Set<Long> excluded, Long publisherId, WampList payload, WampDict payloadKw) throws Exception 
    {
        if(metaTopic == null) {
            // EVENT data
            WampProtocol.sendEvents(realm, publicationId, topic, eligible, excluded, publisherId, payload, payloadKw);
        } else {
            // METAEVENT data (WAMP v2)
            WampDict metaEventDetails = payloadKw;
            WampProtocol.sendMetaEvents(realm, publicationId, topic, metaTopic, eligible, metaEventDetails);
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
                System.out.println ("Received message from broker.");
                //String excludeBroker = receivedMessageFromBroker.getStringProperty("excludeBroker");
                //if(excludeBroker != null && excludeBroker.equals(brokerId)) return;
                
                String topicName = receivedMessageFromBroker.getStringProperty("topic");
                String realm     = receivedMessageFromBroker.propertyExists("realm") ? receivedMessageFromBroker.getStringProperty("realm") : null;
                String metaTopic = receivedMessageFromBroker.propertyExists("metaTopic") ? receivedMessageFromBroker.getStringProperty("metaTopic") : null;
                Long publisherId = receivedMessageFromBroker.propertyExists("publisherId") ? receivedMessageFromBroker.getLongProperty("publisherId") : null;

                String eventData = ((TextMessage)receivedMessageFromBroker).getText();
                //System.out.println ("Received message from topic " + topicName + ": " + eventData);
                
                WampList event = (eventData!=null)? (WampList)WampObject.getSerializer(WampEncoding.JSon).deserialize(eventData) : null;
                WampList payload = (WampList)event.get(0);  // EVENT.payload|list or METAEVENT.MetaEvent|any
                WampDict payloadKw = (WampDict)event.get(1);
                Set<Long> eligible = receivedMessageFromBroker.propertyExists("eligible")? parseSessionIDs(receivedMessageFromBroker.getStringProperty("eligible")) : null;
                Set<Long> exclude  = receivedMessageFromBroker.propertyExists("exclude")?  parseSessionIDs(receivedMessageFromBroker.getStringProperty("exclude"))  : null;

                for(WampSubscription subscription : WampBroker.getTopic(topicName).getSubscriptions()) {
                    for(WampTopic topic : subscription.getTopics()) {
                        broadcastClusterEventToLocalNodeClients(realm, subscription.getId(), topic, metaTopic, eligible, exclude, publisherId, payload, payloadKw);
                    }
                }
                
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error receiving message from broker", ex);
            }
        }
        
    }
    
}
