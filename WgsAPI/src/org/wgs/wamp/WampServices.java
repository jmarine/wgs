package org.wgs.wamp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.wgs.util.MessageBroker;


public class WampServices 
{
    private static final Logger logger = Logger.getLogger(MessageBroker.class.getName());
    
    private static TreeMap<String,WampApplication> apps = new TreeMap<String,WampApplication>();
    
    private static TreeMap<String,WampTopic> topics = new TreeMap<String,WampTopic>();
    
    private static TreeMap<String,WampTopicPattern> topicPatterns = new TreeMap<String,WampTopicPattern>();
    
    
    public static void registerApplication(String name, WampApplication wampApp)
    {
        apps.put(name, wampApp);
    }
    
    
    public static void unregisterApplication(String name)
    {
        apps.remove(name);
    }
    
    public static WampApplication getApplication(String name)
    {
        return apps.get(name);
    }
    
    public static Collection<WampApplication> getApplications()
    {
        return apps.values();
    }
    
    
    
    public static WampTopic createTopic(WampApplication app, String topicFQname, WampTopicOptions options)
    {
        WampTopic topic = topics.get(topicFQname);
        if(topic == null) {
            topic = new WampTopic(topicFQname, options);
            topics.put(topicFQname, topic);
            
            for(WampTopicPattern topicPattern : topicPatterns.values()) {
                if(isTopicUriMatchingWithWildcards(topicFQname, topicPattern.getTopicUriPattern(), topicPattern.getMatchType())) {
                    topicPattern.getTopics().add(topic);
                    for(WampSubscription patternSubscription : topicPattern.getSubscriptions()) {
                        try { 
                            subscribeClientWithTopic(app, patternSubscription.getSocket(), topic.getURI(), patternSubscription.getOptions());
                        } catch(Exception ex) {
                            logger.log(Level.FINE, "Error in subscription to topic", ex);
                        }                      
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
                try { 
                    unsubscribeClientFromTopic(app, subscription.getSocket(), topicFQname, subscription.getOptions());
                } catch(Exception ex) {
                    logger.log(Level.FINE, "Error in unsubscription to topic", ex);
                }                      
            }
            
            for(WampTopicPattern topicPattern : topicPatterns.values()) {
                if(isTopicUriMatchingWithWildcards(topicFQname, topicPattern.getTopicUriPattern(), topicPattern.getMatchType())) {
                    topicPattern.getTopics().remove(topic);
                }
            }            
        } 
        return topic;
    }
    

    public static boolean isTopicUriMatchingWithWildcards(String topicFQname, String topicUrlPattern, WampSubscriptionOptions.MatchEnum matchType) 
    {
        String regexp = (matchType==WampSubscriptionOptions.MatchEnum.prefix)? topicUrlPattern.replace("*", ".*") : topicUrlPattern.replace("*", ".+");
        return (topicFQname.matches(regexp));
    }

    
    public static WampTopic getTopic(String topicFQname)
    {
        WampTopic topic = topics.get(topicFQname);
        return topic;
    }  
    
    
    public static Collection<WampTopic> getTopics(WampSubscriptionOptions.MatchEnum matchType, String topicUriOrPattern)
    {
        String topicPatternKey = matchType.toString() + ">" + topicUriOrPattern;
        WampTopicPattern topicPattern = topicPatterns.get(topicPatternKey);
        
        if(topicPattern != null) {
            
            return topicPattern.getTopics();
            
        } else {
        
            if(matchType != WampSubscriptionOptions.MatchEnum.exact) {  // prefix or wildcards
                ArrayList<WampTopic> retval = new ArrayList<WampTopic>();
                int wildcardPos = topicUriOrPattern.indexOf("*");
                String topicUriBegin = topicUriOrPattern.substring(0, wildcardPos);
                String topicUriEnd = topicUriBegin + "~";
                NavigableMap<String,WampTopic> navMap = topics.subMap(topicUriBegin, true, topicUriEnd, false);
                for(WampTopic topic : navMap.values()) {
                    if(isTopicUriMatchingWithWildcards(topic.getURI(), topicUriOrPattern, matchType)) {
                        retval.add(topic);
                    }
                }
                return retval;
            } else {                
                ArrayList<WampTopic> retval = new ArrayList<WampTopic>();
                WampTopic topic = getTopic(topicUriOrPattern);
                if(topic != null) retval.add(topic);
                return retval;
            }
        }
    }
    
    
    public static void processPublishMessage(WampApplication app, WampSocket clientSocket, ArrayNode request) throws Exception 
    {
        String topicName = clientSocket.normalizeURI(request.get(1).asText());
        WampTopic topic = WampServices.getTopic(topicName);
        try {
            WampModule module = app.getWampModule(topic.getBaseURI(), app.getDefaultWampModule());
            module.onPublish(clientSocket, topic, request);
        } catch(Exception ex) {
            logger.log(Level.FINE, "Error in publishing to topic", ex);
        }  
    }
    
    
    
    public static Collection<WampTopic> subscribeClientWithTopic(WampApplication app, WampSocket clientSocket, String topicUriOrPattern, WampSubscriptionOptions options)
    {
        // FIXME: merge subscriptions options (events & metaevents),
        // when the 1st eventhandler and 1st metahandler is subscribed
        topicUriOrPattern = clientSocket.normalizeURI(topicUriOrPattern);
        if(options == null) options = new WampSubscriptionOptions(null);
        if(options.getMatchType() == WampSubscriptionOptions.MatchEnum.prefix && !topicUriOrPattern.endsWith("*")) {
            topicUriOrPattern = topicUriOrPattern + "*";
        }
        
        Collection<WampTopic> topics = WampServices.getTopics(options.getMatchType(), topicUriOrPattern);
        
        if(options.getMatchType() != WampSubscriptionOptions.MatchEnum.exact) {  // prefix or wildcards
            String topicPatternKey = options.getMatchType().toString() + ">" + topicUriOrPattern;
            WampTopicPattern topicPattern = topicPatterns.get(topicPatternKey);
            if(topicPattern == null) {
                topicPattern = new WampTopicPattern(options.getMatchType(), topicUriOrPattern, topics);
                topicPatterns.put(topicPatternKey, topicPattern);
            }
            
            WampSubscription subscription = topicPattern.getSubscription(clientSocket.getSessionId());
            if(subscription == null) subscription = new WampSubscription(clientSocket, topicUriOrPattern, options);
            if(subscription.refCount(+1) == 1) topicPattern.addSubscription(subscription);
            //clientSocket.addSubscription(subscription);
        } 
        
        for(WampTopic topic : topics) {
            WampModule module = app.getWampModule(topic.getBaseURI(), app.getDefaultWampModule());
            try { 
                module.onSubscribe(clientSocket, topic, options);
            } catch(Exception ex) {
                if(options != null && options.hasMetaEvents()) {
                    try { 
                        ObjectNode metaevent = (new ObjectMapper()).createObjectNode();
                        metaevent.put("error", ex.getMessage());
                        publishMetaEvent(topic, WampMetaTopic.DENIED, metaevent, clientSocket);
                        logger.log(Level.FINE, "Error in subscription to topic", ex);
                    } catch(Exception ex2) { }
                }
            }
        }
    
        return topics;
    }
    
    
    public static Collection<WampTopic> unsubscribeClientFromTopic(WampApplication app, WampSocket clientSocket, String topicUriOrPattern, WampSubscriptionOptions options)
    {
        topicUriOrPattern = clientSocket.normalizeURI(topicUriOrPattern);
        if(options == null) options = new WampSubscriptionOptions(null);
        if(options.getMatchType() == WampSubscriptionOptions.MatchEnum.prefix && !topicUriOrPattern.endsWith("*")) {
            topicUriOrPattern = topicUriOrPattern + "*";
        }
        
        Collection<WampTopic> topics = getTopics(options.getMatchType(), topicUriOrPattern);
        if(options.getMatchType() != WampSubscriptionOptions.MatchEnum.exact) {  // prefix or wildcard
            String topicPatternKey = options.getMatchType().toString() + ">" + topicUriOrPattern;
            WampTopicPattern topicPattern = topicPatterns.get(topicPatternKey);
            WampSubscription subscription = topicPattern.getSubscription(clientSocket.getSessionId());
            if(subscription.refCount(-1) <= 0) topicPattern.removeSubscription(subscription);
            /** Don't clear topicPatterns for future clients
            // topicPatterns.remove(topicPatternKey);
            */
        }
        
        for(WampTopic topic : topics) {
            WampSubscription subscription = topic.getSubscription(clientSocket.getSessionId());
            if(subscription != null) {
                try { 
                    WampModule module = app.getWampModule(topic.getBaseURI(), app.getDefaultWampModule());
                    module.onUnsubscribe(clientSocket, topic);
                } catch(Exception ex) {
                    logger.log(Level.FINE, "Error in unsubscription to topic", ex);
                }          
            }
        }
        
        return topics;
    }

    
    public static void publishEvent(String publisherId, WampTopic topic, JsonNode event, WampPublishOptions options) 
    {
        logger.log(Level.INFO, "Broadcasting to {0}: {1}", new Object[]{topic.getURI(),event});
        try {
            MessageBroker.publish(topic, 0L, event, null, options.getEligible(), options.getExcluded(), (options.hasIdentifyMe()? publisherId : null));
        } catch(Exception ex) {
            logger.log(Level.SEVERE, "Error in publishing event to topic", ex);
        }
    }   
    
    public static void publishMetaEvent(WampTopic topic, String metatopic, JsonNode metaevent, WampSocket toClient) 
    {
        logger.log(Level.INFO, "Broadcasting to {0}: {1}", new Object[]{topic.getURI(),metaevent});
        try {
            HashSet<String> eligible = new HashSet<String>();
            if(toClient != null) eligible.add(toClient.getSessionId());
            MessageBroker.publish(topic, 0L, metaevent, metatopic, eligible, null, null);
        } catch(Exception ex) {
            logger.log(Level.SEVERE, "Error in publishing metaevent to topic", ex);
        }        
    }    
    
}
