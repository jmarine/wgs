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
import org.wgs.wamp.jms.WampTopicConnectionFactory;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;



public class JmsServices
{
    private static final Logger logger = Logger.getLogger(JmsServices.class.getName());

    private static String           clusterEnabled = null;
    private static String           clusterType    = null;
    private static boolean          brokerTested = false;
    private static boolean          brokerAvailable = false;
    public  static String           brokerId = "wgs-" + UUID.randomUUID().toString();

    private static String           wgsClusterNodeEndpoint = null;
    private static WampTopic        wgsClusterTopic = new WampTopic("wgs.cluster", null);
    private static WampTopic        wgsClusterNodeTopic = new WampTopic("wgs.cluster." + brokerId, null);
    
    
    private static TopicConnection  reusableTopicConnection = null;
    private static ConcurrentHashMap<WampTopic,TopicSubscriber> topicSubscribers = new ConcurrentHashMap<WampTopic,TopicSubscriber>();
    private static ConcurrentHashMap<TopicConnection,TopicSession> topicSessionsByConnection = new ConcurrentHashMap<TopicConnection,TopicSession>();
   
    
    public static void start(Properties serverConfig) throws Exception
    {
        clusterEnabled = serverConfig.getProperty("cluster.enabled");
        clusterType = serverConfig.getProperty("cluster.type");
        if(clusterEnabled != null && clusterEnabled.equalsIgnoreCase("true")) {
            TopicConnectionFactory tcf = null;
            switch(clusterType) {
                case "imq":
                    tcf = EmbeddedOpenMQ.start(serverConfig);
                    break;
                case "wamp":
                    String wampClusterUrl = serverConfig.getProperty("cluster.wamp.server_url");
                    String wampClusterRealm = serverConfig.getProperty("cluster.wamp.realm");
                    tcf = new WampTopicConnectionFactory(WampEncoding.MsgPack, wampClusterUrl, wampClusterRealm, false);
                    break;
            }
            
            InitialContext jndi = new InitialContext();
            jndi.bind("jms/ClusterTopicConnectionFactory", tcf);
        }

        wgsClusterNodeEndpoint = serverConfig.getProperty("cluster.wamp.node_url");
        WampDict metaData = new WampDict();
        metaData.put("_wgsClusterNodeEndpoint", wgsClusterNodeEndpoint);
        metaData.put("_wgsTicket", brokerId);  // TODO: use one time password.
        subscribeMessageListener(wgsClusterTopic);
        subscribeMessageListener(wgsClusterNodeTopic);
        publishMetaEvent("cluster", WampProtocol.newGlobalScopeId(), wgsClusterTopic, "wgs.cluster.node_attached", metaData, null);
    }

    public static void stop() throws Exception
    {
        WampDict metaData = new WampDict();
        metaData.put("_wgsClusterNodeEndpoint", wgsClusterNodeEndpoint);
        publishMetaEvent("cluster", WampProtocol.newGlobalScopeId(), wgsClusterTopic, "wgs.cluster.node_detached", metaData, null);
        unsubscribeMessageListener(wgsClusterNodeTopic);
        unsubscribeMessageListener(wgsClusterTopic);

        if(reusableTopicConnection != null) {
            try {
                //reusableTopicConnection.stop();
                reusableTopicConnection.close();
            } catch(Exception ex) { }
        }
        
        if(clusterEnabled != null && clusterEnabled.equalsIgnoreCase("true") 
                && clusterType != null && clusterType.equals("imq")) {
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
            TopicConnectionFactory tcf = (TopicConnectionFactory)jndi.lookup("jms/ClusterTopicConnectionFactory");
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
            if(isJmsBrokerAvailable() && !topicSubscribers.containsKey(wampTopic)) {
                String topicName = wampTopic.getTopicName();
                TopicConnection connection = getTopicConnectionFromPool();

                TopicSession subscriberSession = topicSessionsByConnection.get(connection);
                if(subscriberSession == null) {
                    subscriberSession = connection.createTopicSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE);
                    topicSessionsByConnection.put(connection, subscriberSession);
                }
                
                Topic jmsTopic = subscriberSession.createTopic(normalizeTopicName(topicName));
                String selector = "";
                synchronized(connection) {
                    connection.stop();
                    TopicSubscriber subscriber = subscriberSession.createSubscriber(jmsTopic, selector, false);
                    subscriber.setMessageListener(new BrokerMessageListener());
                    connection.start();
                    topicSubscribers.put(wampTopic, subscriber);
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
                TopicSubscriber subscriber = topicSubscribers.remove(topic);
                subscriber.close();
                
            } catch(Exception ex) { }
        }
    }
    
    
    static void publishEvent(String realm, Long id, WampTopic wampTopic, String metaTopic, WampList payload, WampDict payloadKw, Set<Long> eligible, Set<Long> exclude, WampDict eventDetails) throws Exception
    {
        if(eventDetails == null) {
            eventDetails = new WampDict();
        }
        
        if(!isJmsBrokerAvailable()) {
            broadcastClusterEventToLocalNodeClients(realm, id, wampTopic, metaTopic, payload, payloadKw, eligible, exclude, eventDetails);
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
            String eventPayLoad = (event != null)? (String)WampEncoding.JSON.getSerializer().serialize(event) : null;
            TextMessage msg = pubSession.createTextMessage(eventPayLoad);
            
            msg.setLongProperty("_jms_msgid", id);
            msg.setStringProperty("_jms_destination", topicName);
            
            if(eventDetails.has("publisher")) msg.setLongProperty("publisher", eventDetails.getLong("publisher"));
            if(eventDetails.has("authid")) msg.setStringProperty("authid", eventDetails.getText("authid"));
            if(eventDetails.has("authprovider")) msg.setStringProperty("authprovider", eventDetails.getText("authprovider"));
            if(eventDetails.has("authrole")) msg.setStringProperty("authrole", eventDetails.getText("authrole"));

            if(realm != null)       msg.setStringProperty("_realm", realm);
            if(metaTopic != null)   msg.setStringProperty("_metaTopic", metaTopic);
            if(eligible != null)    msg.setStringProperty("_eligible", serializeSessionIDs(eligible));
            if(exclude != null)     msg.setStringProperty("_exclude", serializeSessionIDs(exclude));

            msg.setStringProperty("_brokerId", brokerId);
            
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
        
        publishEvent(realm, publicationId, topic, metatopic, null, metaEventDetails, eligible, null, metaEventDetails);
    } 
    
    
    private static void broadcastClusterEventToLocalNodeClients(String realm, Long publicationId, WampTopic topic, String metaTopic, WampList payload, WampDict payloadKw, Set<Long> eligible, Set<Long> excluded, WampDict eventDetails) throws Exception 
    {
        if(metaTopic == null) {
            // EVENT data
            WampProtocol.sendEvents(realm, publicationId, topic, payload, payloadKw, eligible, excluded, eventDetails);
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
            StringTokenizer stk = new StringTokenizer(ids, ", " , false);
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
                String publisherBrokerId = receivedMessageFromBroker.getStringProperty("_brokerId");
                System.out.println ("Received message from broker: " + publisherBrokerId);

                Long   publicationId = receivedMessageFromBroker.getLongProperty("_jms_msgid");
                String topicName = receivedMessageFromBroker.getStringProperty("_jms_destination");
                String realm     = receivedMessageFromBroker.propertyExists("_realm") ? receivedMessageFromBroker.getStringProperty("_realm") : null;
                String metaTopic = receivedMessageFromBroker.propertyExists("_metaTopic") ? receivedMessageFromBroker.getStringProperty("_metaTopic") : null;
                Long   publisherId = receivedMessageFromBroker.propertyExists("publisher") ? receivedMessageFromBroker.getLongProperty("publisher") : null;
                String publisherAuthId = receivedMessageFromBroker.propertyExists("authid") ? receivedMessageFromBroker.getStringProperty("authid") : null;
                String publisherAuthProvider = receivedMessageFromBroker.propertyExists("authprovider") ? receivedMessageFromBroker.getStringProperty("authprovider") : null;
                String publisherAuthRole = receivedMessageFromBroker.propertyExists("authrole") ? receivedMessageFromBroker.getStringProperty("authrole") : null;

                String eventData = ((TextMessage)receivedMessageFromBroker).getText();
                //System.out.println ("Received message from topic " + topicName + ": " + eventData);
                
                WampList event = (eventData!=null)? (WampList)WampEncoding.JSON.getSerializer().deserialize(eventData, 0, eventData.length()) : null;
                WampList payload = (WampList)event.get(0);  // EVENT.payload|list or METAEVENT.MetaEvent|any
                WampDict payloadKw = (WampDict)event.get(1);
                Set<Long> eligible = receivedMessageFromBroker.propertyExists("_eligible")? parseSessionIDs(receivedMessageFromBroker.getStringProperty("_eligible")) : null;
                Set<Long> exclude  = receivedMessageFromBroker.propertyExists("_exclude")?  parseSessionIDs(receivedMessageFromBroker.getStringProperty("_exclude"))  : null;

                if(topicName.startsWith(wgsClusterTopic.getTopicName())) {

                    if(publisherBrokerId != null && !publisherBrokerId.equals(brokerId)) {  // exclude me
                        String wgsTicket = payloadKw.getText("_wgsTicket");
                        String wgsRemoteClusterNodeEndpoint = payloadKw.getText("_wgsClusterNodeEndpoint");
                        switch(metaTopic) {
                            case "wgs.cluster.node_attached":
                                WampDict metaData = new WampDict();
                                metaData.put("_wgsClusterNodeEndpoint", wgsClusterNodeEndpoint);
                                metaData.put("_wgsTicket", brokerId);  // TODO: use one time password.
                                WampCluster.addNode(wgsRemoteClusterNodeEndpoint, new WampCluster.Node(publisherBrokerId, wgsRemoteClusterNodeEndpoint, wgsTicket));
                                publishMetaEvent("cluster", WampProtocol.newGlobalScopeId(), new WampTopic(wgsClusterTopic.getTopicName() + "." + publisherBrokerId, null), "wgs.cluster.node_presence", metaData, null);
                                break;
                            case "wgs.cluster.node_presence":
                                WampCluster.addNode(wgsRemoteClusterNodeEndpoint, new WampCluster.Node(publisherBrokerId, wgsRemoteClusterNodeEndpoint, wgsTicket));
                                break;
                            case "wgs.cluster.node_detached":
                                WampCluster.removeNode(wgsRemoteClusterNodeEndpoint);
                                break;                                
                        }
                    }


                } else {
                    WampTopic topic = WampBroker.getTopic(topicName);
                    if(topic != null) {
                        WampDict eventDetails = new WampDict();
                        if(publisherId != null) eventDetails.put("publisher", publisherId);            
                        if(publisherAuthId != null) eventDetails.put("authid", publisherAuthId);
                        if(publisherAuthProvider != null) eventDetails.put("authprovider", publisherAuthProvider);
                        if(publisherAuthRole != null) eventDetails.put("authrole", publisherAuthRole);
                        
                        broadcastClusterEventToLocalNodeClients(realm, publicationId, topic, metaTopic, payload, payloadKw, eligible, exclude, eventDetails);
                    }
                }

                
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error receiving message from broker", ex);
                System.err.println("Error: " + ex.getClass().getName() + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        
    }
    
}
