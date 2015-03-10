package org.wgs.wamp;

import java.util.Collection;
import java.util.HashMap;

import org.wgs.wamp.client.WampClient;
import org.wgs.wamp.rpc.WampCalleeRegistration;
import org.wgs.wamp.rpc.WampRemoteMethod;
import org.wgs.wamp.topic.JmsServices;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


public class WampCluster
{
    private static HashMap<String, Node> nodes = new HashMap<String, Node>();
    
    public static void addNode(String uri, Node node) throws Exception
    {
        nodes.put(uri, node);
        System.out.println("Cluster node added: " + uri);
        node.start();
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
            client.registerRPC(options, remoteMethod.getProcedureURI(), remoteMethod);
        }

        public static void unregisterClusteredRPC(WampClient client, WampRealm realm, WampCalleeRegistration registration, WampRemoteMethod remoteMethod)
        {
            client.unregisterRPC(remoteMethod.getProcedureURI());
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
            client.connect();
            
            WampDict authDetails = new WampDict();
            authDetails.put("authmethods", new WampList("ticket"));
            authDetails.put("authid", JmsServices.brokerId);
            authDetails.put("ticket", wgsTicket);
            
            client.hello("cluster", authDetails);
            client.waitResponses();
            
            System.out.println("Cluster node is active!");
        }
        
        public void stop() throws Exception
        {
            client.close();
        }
    }
    
}
