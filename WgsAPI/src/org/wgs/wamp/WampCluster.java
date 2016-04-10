package org.wgs.wamp;

import java.util.Collection;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InitialContext;

import org.wgs.wamp.annotation.WampSubscribe;
import org.wgs.wamp.client.WampClient;
import org.wgs.wamp.rpc.WampCalleeRegistration;
import org.wgs.wamp.rpc.WampRemoteMethod;
import org.wgs.wamp.topic.WampPublishOptions;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampMatchType;


public class WampCluster extends WampModule
{
    public  static String       brokerId = "wgs-" + UUID.randomUUID().toString();
    private static Logger       logger = Logger.getLogger(WampCluster.class.getName());
    
    private static WampCluster  cluster = null;
    private static String       clusterEnabled = null;
    private static String       wgsClusterNodeEndpoint = null;
    
    private static final String wgsClusterTopicName = "wgs.cluster";
    
    private static WampClient   masterConnection = null;
    public  static int          clusterNodeListSyncMaxMillis = 100;
    
    
    private static HashMap<String, Node> nodes = new HashMap<String, Node>();

    static {
        String maxMillis = System.getProperty("WGS_CLUSTER_NODE_LIST_SYNC_MAX_MILLIS");
        if(maxMillis != null) {
            try { clusterNodeListSyncMaxMillis = Integer.parseInt(maxMillis); }
            catch(Exception ex) { }
        }
    }

    private WampCluster(WampApplication app) {
        super(app);
    }
    
    
    @WampSubscribe(topic = wgsClusterTopicName, match = WampMatchType.exact)
    public void onClusterEvent(WampSocket serverSocket, Long subscriptionId, Long publicationId, WampDict details, WampList payload, WampDict payloadKw) throws Exception
    {
        //String topic = masterConnection.getTopicFromEventData(subscriptionId, details);

        try {
            String publisherBrokerId = payloadKw.getText("wgsBrokerId");
            System.out.println ("Received message from broker: " + publisherBrokerId);

            if(publisherBrokerId != null && !publisherBrokerId.equals(brokerId)) {  // exclude me
                String wgsTicket = payloadKw.getText("wgsTicket");
                String wgsRemoteClusterNodeEndpoint = payloadKw.getText("wgsClusterNodeEndpoint");
                String wgsClusterEventType = payloadKw.getText("wgsClusterEventType");
                switch(wgsClusterEventType) {
                    case "wgs.cluster.node_attached":
                        Long publisherSessionId = details.getLong("publisher");
                        WampCluster.addNode(wgsRemoteClusterNodeEndpoint, new WampCluster.Node(publisherBrokerId, wgsRemoteClusterNodeEndpoint, wgsTicket));
                        publishClusterNodeEvent(publisherSessionId, "wgs.cluster.node_presence");
                        break;
                    case "wgs.cluster.node_presence":
                        WampCluster.addNode(wgsRemoteClusterNodeEndpoint, new WampCluster.Node(publisherBrokerId, wgsRemoteClusterNodeEndpoint, wgsTicket));
                        break;
                    case "wgs.cluster.node_detached":
                        WampCluster.removeNode(wgsRemoteClusterNodeEndpoint);
                        break;    
                    default:
                        break;
                }
            }


        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Error receiving message from broker", ex);
            System.err.println("Error: " + ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace();
        } 

    }
    
    
    
    public static synchronized WampCluster startApplicationNode() 
    {
        if(cluster == null) {
            try {
                InitialContext ctx = new InitialContext();
                clusterEnabled = (String)ctx.lookup("java:comp/env/cluster/enabled");
                if(clusterEnabled != null && !"false".equals(clusterEnabled.toLowerCase())) {
                    String wampClusterRealm = (String)ctx.lookup("java:comp/env/cluster/wamp_realm");
                    String wampClusterUrl   = (String)ctx.lookup("java:comp/env/cluster/wamp_server_url");
                    wgsClusterNodeEndpoint  = (String)ctx.lookup("java:comp/env/cluster/wamp_node_url");

                    masterConnection = new WampClient(wampClusterUrl);
                    cluster = new WampCluster(masterConnection.getWampApplication());
                    masterConnection.getWampApplication().registerWampModule(cluster);
                    masterConnection.connect();
                    masterConnection.hello(wampClusterRealm, null, null, false);
                    masterConnection.waitResponses();

                    publishClusterNodeEvent(null, "wgs.cluster.node_attached");
                    Thread.sleep(clusterNodeListSyncMaxMillis);  // FIXME: wait for cluster node list synchronization
                }
            } catch(Exception ex) {
                cluster = null;
            }                
        }
        return cluster;
    }    
    
    private static void publishClusterNodeEvent(Long toNode, String wgsClusterEventType) throws Exception
    {
        WampDict eventData = new WampDict();
        eventData.put("wgsBrokerId", brokerId);
        eventData.put("wgsClusterEventType", wgsClusterEventType);
        eventData.put("wgsClusterNodeEndpoint", wgsClusterNodeEndpoint);
        eventData.put("wgsTicket", brokerId); 

        WampPublishOptions options = new WampPublishOptions();
        options.setDiscloseMe(true);
        if(toNode != null) {
            options.setEligible(java.util.Collections.singleton(toNode));
        }
        
        masterConnection.publish(wgsClusterTopicName, null, eventData, options.toWampObject());
    }
    
    
    public static void stop() throws Exception
    {
        if(clusterEnabled != null && !"false".equals(clusterEnabled.toLowerCase())) {
            publishClusterNodeEvent(null, "wgs.cluster.node_detached");
            masterConnection.close();
        }
    }    
    
    
    public static void addNode(String uri, Node node) throws Exception
    {
        node.start();
        nodes.put(uri, node);
        System.out.println("Cluster node added: " + uri);
    }
    
    
    public static void removeNode(String uri) throws Exception
    {
        Node node = nodes.remove(uri);
        System.out.println("Cluster node removed: " + uri);
        node.stop();
    }
    
    
    public static Collection<Node> getNodes()
    {
        return nodes.values();
    }

    
    
    public static class Node
    {
        private String brokerId;
        private String wgsClusterNodeEndpoint;
        private String wgsTicket;
        
        private WampClient client;
        
        public Node(String brokerId, String wgsClusterNodeEndpoint, String wgsTicket) throws Exception
        {
            this.brokerId = brokerId;
            this.wgsClusterNodeEndpoint = wgsClusterNodeEndpoint;
            this.wgsTicket = wgsTicket;
        }
        
        public WampClient getWampClient()
        {
            return this.client;
        }
        
        public static void registerClusteredRPC(WampClient client, WampRealm realm, WampCalleeRegistration registration, WampRemoteMethod remoteMethod) 
        {
            WampDict options = new WampDict();
            options.put("_cluster_peer_realm", realm.getRealmName());            
            options.put("_cluster_peer_sid", remoteMethod.getRemotePeer().getWampSessionId());
            options.put("match", registration.getMatchType().toString());
            try { 
                client.registerRPC(options, remoteMethod.getProcedureURI(), remoteMethod); 
            } catch(Exception ex) {
                System.out.println("Error: registerClusteredRPC: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        public static void unregisterClusteredRPC(WampClient client, WampRealm realm, WampCalleeRegistration registration, WampRemoteMethod remoteMethod) 
        {
            try { 
                client.unregisterRPC(remoteMethod.getProcedureURI()); 
            } catch(Exception ex) {
                System.out.println("WampCluster.unregisterClusteredRPC: error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }        
        
        public void start() throws Exception
        {
            client = new WampClient(wgsClusterNodeEndpoint);
            client.getWampApplication().registerWampModule(new WampModule(client.getWampApplication()) {
                @Override
                public void onWampSessionEstablished(WampSocket clientSocket, WampDict details) 
                { 
                    super.onWampSessionEstablished(clientSocket, details);
                    if("cluster".equals(clientSocket.getRealm())) {
                        for(String realmName : WampRealm.getRealmNames()) {
                            if(!"cluster".equals(realmName)) {
                                WampRealm realm = WampRealm.getRealm(realmName);
                                for(Long registrationId : realm.getRegistrationIds()) {
                                    WampCalleeRegistration registration = realm.getRegistration(registrationId);
                                    for(WampRemoteMethod remoteMethod : registration.getRemoteMethods(null, null)) {
                                        if(!"cluster".equals(remoteMethod.getRemotePeer().getRealm())) {
                                            WampCluster.Node.registerClusteredRPC(client, realm, registration, remoteMethod);
                                        }
                                    }
                                }
                                for(WampCalleeRegistration registration : realm.getPatternRegistrations()) {                
                                    for(WampRemoteMethod remoteMethod : registration.getRemoteMethods(null, null)) {
                                        if(!"cluster".equals(remoteMethod.getRemotePeer().getRealm())) {
                                            WampCluster.Node.registerClusteredRPC(client, realm, registration, remoteMethod);
                                        }
                                    }
                                }
                            }
                        }    

                    }                    
                }
            });
            
            WampDict authDetails = new WampDict();
            authDetails.put("authmethods", new WampList("ticket"));
            authDetails.put("authid", brokerId);
            authDetails.put("ticket", wgsTicket);

            client.connect();
            
            client.hello("cluster", authDetails);
            client.waitResponses();
            
            System.out.println("Cluster node is active!");
        }
        
        public void stop() throws Exception
        {
            if(client != null) {
                client.close();
            }
        }
    }
    
    
}
