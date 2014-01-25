package org.wgs.wamp.topic;

import org.wgs.wamp.types.WampList;
import org.wgs.wamp.topic.WampTopic;
import org.wgs.wamp.topic.WampTopicOptions;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampSubscriptionOptions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.wgs.wamp.types.WampMatchType;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.topic.JmsServices;
import org.wgs.wamp.types.WampDict;


public class Broker 
{
    private static final Logger logger = Logger.getLogger(JmsServices.class.getName());
    
    private static TreeMap<String,WampTopic> topics = new TreeMap<String,WampTopic>();
    
    private static TreeMap<Long,WampSubscription>   topicSubscriptionsById = new TreeMap<Long,WampSubscription>();
    private static TreeMap<String,WampSubscription> topicSubscriptionsByTopicURI = new TreeMap<String,WampSubscription>();
    private static TreeMap<String,WampSubscription> topicPatterns = new TreeMap<String,WampSubscription>();
    

    

    public static WampTopic createTopic(WampApplication app, String topicFQname, WampTopicOptions options)
    {
        WampTopic topic = topics.get(topicFQname);
        if(topic == null) {
            topic = new WampTopic(topicFQname, options);
            topics.put(topicFQname, topic);

            for(WampSubscription subscription : topicPatterns.values()) {
                if(isUriMatchingWithRegExp(topicFQname, subscription.getTopicRegExp())) {
                    subscription.getTopics().add(topic);

                    try { 
                        for(Long sid : subscription.getSessionIds()) {
                            WampSocket socket = subscription.getSocket(sid);
                            WampSubscriptionOptions exactTopicOpt = new WampSubscriptionOptions(null);
                            exactTopicOpt.setMatchType(WampMatchType.exact);
                            exactTopicOpt.setEventsEnabled(subscription.getOptions().hasEventsEnabled());
                            exactTopicOpt.setMetaTopics(subscription.getOptions().getMetaTopics());
                            
                            WampModule module = app.getWampModule(topic.getBaseURI(), app.getDefaultWampModule());
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
        WampTopic topic = topics.get(topicFQname);
        if(topic != null) {
            topics.remove(topicFQname);
            
            for(WampSubscription subscription : topic.getSubscriptions()) {
                for(Long sid : subscription.getSessionIds()) {
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
                    if(isUriMatchingWithRegExp(topic.getURI(), regExp)) {
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
        String topicName = clientSocket.normalizeURI(request.getText(3));
        WampTopic topic = Broker.getTopic(topicName);
        if(topic == null) topic = Broker.createTopic(app, topicName, null);
        
        try {
            WampModule module = app.getWampModule(topic.getBaseURI(), app.getDefaultWampModule());
            module.onPublish(clientSocket, topic, request);
        } catch(Exception ex) {
            logger.log(Level.FINE, "Error in publishing to topic", ex);
        }  
    }
    
    public static void publishEvent(Long id, WampTopic wampTopic, WampList payload, WampDict payloadKw, Set<Long> eligible, Set<Long> exclude, Long publisherId) throws Exception
    {
        JmsServices.publishEvent(id, wampTopic, null, payload, payloadKw, eligible, exclude, publisherId);
    }

    public static void publishMetaEvent(Long id, WampTopic wampTopic, String metatopic, WampDict metaEventDetails, Long toClient) throws Exception
    {
        JmsServices.publishMetaEvent(id, wampTopic, metatopic, metaEventDetails, toClient);
    }
    
    
    public static WampSubscription getSubscriptionById(Long subscriptionId)
    {
        return topicSubscriptionsById.get(subscriptionId);
    }    
    
    public static Collection<WampTopic> subscribeClientWithTopic(WampApplication app, WampSocket clientSocket, Long requestId, String topicUriOrPattern, WampSubscriptionOptions options)
    {
        topicUriOrPattern = clientSocket.normalizeURI(topicUriOrPattern);
        if(options == null) options = new WampSubscriptionOptions(null);
        if(options.getMatchType() == WampMatchType.prefix && !topicUriOrPattern.endsWith("..")) {
            topicUriOrPattern = topicUriOrPattern + "..";
        }
        
        WampSubscription subscription = topicSubscriptionsByTopicURI.get(topicUriOrPattern);
        if(subscription == null) {
            Long subscriptionId = WampProtocol.newId();  
            Collection<WampTopic> matchingTopics = Broker.getTopics(app, options.getMatchType(), topicUriOrPattern);            
            subscription = new WampSubscription(subscriptionId, options.getMatchType(), topicUriOrPattern, matchingTopics, options);
            topicSubscriptionsById.put(subscriptionId, subscription);
            topicSubscriptionsByTopicURI.put(topicUriOrPattern, subscription);
            if(options.getMatchType() != WampMatchType.exact) topicPatterns.put(topicUriOrPattern, subscription);
        }        
        
        subscription.addSocket(clientSocket);
        WampProtocol.sendSubscribed(clientSocket, requestId, subscription.getId());

        for(WampTopic topic : subscription.getTopics()) {
            WampModule module = app.getWampModule(topic.getBaseURI(), app.getDefaultWampModule());

            try { 
                module.onSubscribe(clientSocket, topic, subscription, options);
            } catch(Exception ex) {
                System.err.println("Error: " + ex.getClass().getName() + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        return subscription.getTopics();
    }
    
    
    public static Collection<WampTopic> unsubscribeClientFromTopic(WampApplication app, WampSocket clientSocket, Long requestId, Long subscriptionId)
    {
        Collection<WampTopic> matchingTopics = null;
        WampSubscription subscription = clientSocket.getSubscription(subscriptionId);
        if(subscription == null) {
            if(requestId != null) WampProtocol.sendError(clientSocket, requestId, null, "wamp.error.no_such_subscription", null, null);            
        } else {
            matchingTopics = subscription.getTopics();
            for(WampTopic topic : matchingTopics) {
                try { 
                    WampModule module = app.getWampModule(topic.getBaseURI(), app.getDefaultWampModule());
                    module.onUnsubscribe(clientSocket, subscriptionId, topic);
                } catch(Exception ex) {
                    logger.log(Level.FINE, "Error in unsubscription to topic", ex);
                }          
            }

            clientSocket.removeSubscription(subscriptionId);

            if(requestId != null) WampProtocol.sendUnsubscribed(clientSocket, requestId);
        }
        
        return matchingTopics;
    }
    
}
