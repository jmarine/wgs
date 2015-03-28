package org.wgs.wamp.topic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.NavigableMap;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.wgs.util.Storage;

import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampCluster;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampMatchType;


public class WampBroker 
{
    private static final Logger logger = Logger.getLogger(WampBroker.class.getName());
    
    private static TreeMap<String,WampTopic> topics = new TreeMap<String,WampTopic>();
    
    private static TreeMap<Long,WampSubscription>   topicSubscriptionsById = new TreeMap<Long,WampSubscription>();
    private static TreeMap<String,WampSubscription> topicSubscriptionsByTopicURI = new TreeMap<String,WampSubscription>();
    private static TreeMap<String,WampSubscription> topicPatterns = new TreeMap<String,WampSubscription>();
    

    public static WampTopic createTopic(WampApplication app, String topicFQname, WampTopicOptions options)
    {
        WampTopic topic = topics.get(topicFQname);
        if(topic == null) {
            topic = new WampTopic(topicFQname, options);
            topic = Storage.saveEntity(topic);
            topics.put(topicFQname, topic);

            for(WampSubscription subscription : topicPatterns.values()) {
                if(isUriMatchingWithRegExp(topicFQname, subscription.getTopicRegExp())) {
                    subscription.getTopics().add(topic);

                    try { 
                        for(Long sid : subscription.getSessionIds(null)) {
                            WampSocket socket = subscription.getSocket(sid);
                            WampSubscriptionOptions exactTopicOpt = new WampSubscriptionOptions(null);
                            exactTopicOpt.setMatchType(WampMatchType.exact);
                            exactTopicOpt.setEventsEnabled(subscription.getOptions().hasEventsEnabled());
                            exactTopicOpt.setMetaTopics(subscription.getOptions().getMetaTopics());
                            
                            WampModule module = app.getWampModule(topic.getTopicName(), app.getDefaultWampModule());
                            module.onSubscribe(socket, topic, subscription, exactTopicOpt);
                        }
                    } catch(Exception ex) {
                        logger.log(Level.FINE, "Error in subscription to topic", ex);
                    }                      

                }
            }
            
        }
        return topic;
    }
    
    
    public static WampTopic removeTopic(WampApplication app, String topicFQname)
    {
        WampTopic topic = topics.remove(topicFQname);
        if(topic != null) {
            for(WampSubscription subscription : topic.getSubscriptions()) {
                for(Long sid : subscription.getSessionIds(null)) {
                    WampSocket client = subscription.getSocket(sid);
                    try { 
                        unsubscribeClientFromTopic(app, client, null, subscription.getId());
                    } catch(Exception ex) {
                        logger.log(Level.FINE, "Error in unsubscription to topic", ex);
                    }                      
                }
                
                if(isUriMatchingWithRegExp(topicFQname, subscription.getTopicRegExp())) {
                    subscription.getTopics().remove(topic);
                }                
            }
            
            Storage.removeEntity(topic);
        }
        
        return topic;
    }
    
    
    public static String getPatternRegExp(WampMatchType matchType, String pattern)
    {
        String regexp = pattern.replace("..", "%").replace(".","\\.");
        regexp = (matchType==WampMatchType.prefix)? regexp.replace("%", ".*") : regexp.replace("%", "\\..+\\.");
        return regexp;
    }

    public static boolean isUriMatchingWithRegExp(String uri, String regExp) 
    {
        return (uri.matches(regExp));
    }

    
    public static WampTopic getTopic(String topicFQname)
    {
        WampTopic topic = topics.get(topicFQname);
        return topic;
    }  
    
    
    public static Collection<WampTopic> getTopics(WampApplication app, WampMatchType matchType, String topicUriOrPattern)
    {
        WampSubscription subscription = topicSubscriptionsByTopicURI.get(topicUriOrPattern);
        
        if(subscription != null) {
            
            return subscription.getTopics();
            
        } else {
        
            if(matchType != WampMatchType.exact) {  // prefix or wildcards
                ArrayList<WampTopic> retval = new ArrayList<WampTopic>();
                int wildcardPos = topicUriOrPattern.indexOf("..");
                String topicUriBegin = topicUriOrPattern.substring(0, wildcardPos);
                String topicUriEnd = topicUriBegin + "~";
                NavigableMap<String,WampTopic> navMap = topics.subMap(topicUriBegin, true, topicUriEnd, false);
                String regExp = getPatternRegExp(matchType, topicUriOrPattern);
                for(WampTopic topic : navMap.values()) {
                    if(isUriMatchingWithRegExp(topic.getTopicName(), regExp)) {
                        retval.add(topic);
                    }
                }
                return retval;
            } else {                
                ArrayList<WampTopic> retval = new ArrayList<WampTopic>();
                WampTopic topic = getTopic(topicUriOrPattern);
                if(topic == null) topic = createTopic(app, topicUriOrPattern, null);
                retval.add(topic);
                return retval;
            }
        }
    }
    
    
    public static void processPublishMessage(WampApplication app, WampSocket clientSocket, WampList request) throws Exception 
    {
        String topicName = request.getText(3);
        WampTopic topic = WampBroker.getTopic(topicName);
        if(topic == null) topic = WampBroker.createTopic(app, topicName, null);
        
        try {
            WampModule module = app.getWampModule(topic.getTopicName(), app.getDefaultWampModule());
            module.onPublish(clientSocket, topic, request);
        } catch(Exception ex) {
            logger.log(Level.FINE, "Error in publishing to topic", ex);
        }  
    }
    

    public static void publishEvent(String realm, Long id, WampTopic topic, WampList payload, WampDict payloadKw, Set<Long> eligible, Set<Long> exclude, WampDict eventDetails, boolean broadcastToClusterNodes) throws Exception
    {
        if(broadcastToClusterNodes) {
            if(eventDetails == null) eventDetails = new WampDict();
            eventDetails.put("_cluster_publication_id", id);
            eventDetails.put("_cluster_realm", realm);
            eventDetails.put("_cluster_authid", eventDetails.getText("authid"));
            eventDetails.put("_cluster_authprovider", eventDetails.getText("authprovider"));
            eventDetails.put("_cluster_authrole", eventDetails.getText("authrole"));
            
            for(WampCluster.Node node : WampCluster.getNodes()) {
                WampProtocol.sendPublishMessage(node.getWampClient().getWampSocket(), id, topic.getTopicName(), payload, payloadKw, eventDetails);
            }
            
            eventDetails.remove("_cluster_publication_id");
            eventDetails.remove("_cluster_realm");
            eventDetails.remove("_cluster_authid");
            eventDetails.remove("_cluster_authprovider");
            eventDetails.remove("_cluster_authrole");
        }
        
        WampProtocol.sendEvents(realm, id, topic, payload, payloadKw, eligible, exclude, eventDetails);        
        
    }

    public static void publishMetaEvent(String realm, Long id, WampTopic topic, String metaTopic, WampDict metaEventDetails, Long toClient, boolean broadcastToClusterNodes) throws Exception
    {
        if(broadcastToClusterNodes) {
            if(metaEventDetails == null) metaEventDetails = new WampDict();
            metaEventDetails.put("_cluster_publication_id", id);
            metaEventDetails.put("_cluster_metatopic", metaTopic);
            metaEventDetails.put("_cluster_realm", realm);
            if(toClient != null) metaEventDetails.put("_cluster_eligible_client", toClient);
            
            for(WampCluster.Node node : WampCluster.getNodes()) {
                WampProtocol.sendPublishMessage(node.getWampClient().getWampSocket(), id, topic.getTopicName(), null, null, metaEventDetails);
            }
            
            metaEventDetails.remove("_cluster_publication_id");
            metaEventDetails.remove("_cluster_metatopic");
            metaEventDetails.remove("_cluster_realm");
            metaEventDetails.remove("_cluster_eligible_client");
        }        
        
        WampProtocol.sendMetaEvents(realm, id, topic, metaTopic, null, metaEventDetails);
        
    }

    
    public static WampSubscription getSubscriptionById(Long subscriptionId)
    {
        return topicSubscriptionsById.get(subscriptionId);
    }    

    
    public static Collection<WampTopic> subscribeClientWithTopic(WampApplication app, WampSocket clientSocket, Long requestId, String topicUriOrPattern, WampSubscriptionOptions options)
    {
        if(options == null) options = new WampSubscriptionOptions(null);
        if(options.getMatchType() == WampMatchType.prefix && !topicUriOrPattern.endsWith("..")) {
            topicUriOrPattern = topicUriOrPattern + "..";
        }
        
        WampSubscription subscription = topicSubscriptionsByTopicURI.get(topicUriOrPattern);
        if(subscription == null) {
            Long subscriptionId = WampProtocol.newRouterScopeId();  
            Collection<WampTopic> matchingTopics = WampBroker.getTopics(app, options.getMatchType(), topicUriOrPattern);            
            subscription = new WampSubscription(subscriptionId, options.getMatchType(), topicUriOrPattern, matchingTopics, options);
            topicSubscriptionsById.put(subscriptionId, subscription);
            topicSubscriptionsByTopicURI.put(topicUriOrPattern, subscription);
            if(options.getMatchType() != WampMatchType.exact) topicPatterns.put(topicUriOrPattern, subscription);
        }        

        
        try {
            WampProtocol.sendSubscribedMessage(clientSocket, requestId, subscription.getId());

            if(subscription.addSocket(clientSocket)) {
                for(WampTopic topic : subscription.getTopics()) {
                    WampModule module = app.getWampModule(topic.getTopicName(), app.getDefaultWampModule());
                    module.onSubscribe(clientSocket, topic, subscription, options);
                }
            }
            
        } catch(Exception ex) {
            WampProtocol.sendErrorMessage(clientSocket, WampProtocol.SUBSCRIBE, requestId, null, "wamp.error.subscription_error", null, null);
        }

        return subscription.getTopics();
    }
    
    
    public static Collection<WampTopic> unsubscribeClientFromTopic(WampApplication app, WampSocket clientSocket, Long requestId, Long subscriptionId)
    {
        Collection<WampTopic> matchingTopics = null;
        WampSubscription subscription = clientSocket.getSubscription(subscriptionId);
        if(subscription == null) {
            if(requestId != null) WampProtocol.sendErrorMessage(clientSocket, WampProtocol.UNSUBSCRIBE, requestId, null, "wamp.error.no_such_subscription", null, null);            
        } else {
            matchingTopics = subscription.getTopics();

            if(subscription.removeSocket(clientSocket.getWampSessionId())) {
            
                for(WampTopic topic : matchingTopics) {
                    try { 
                        WampModule module = app.getWampModule(topic.getTopicName(), app.getDefaultWampModule());
                        module.onUnsubscribe(clientSocket, subscriptionId, topic);
                    } catch(Exception ex) {
                        logger.log(Level.FINE, "Error in unsubscription to topic", ex);
                    } 
                }
            }
                        
            if(requestId != null) WampProtocol.sendUnsubscribedMessage(clientSocket, requestId);
        }
        
        return matchingTopics;
    }
    
}
