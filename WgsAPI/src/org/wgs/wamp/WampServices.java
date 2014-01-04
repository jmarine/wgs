package org.wgs.wamp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.wgs.util.MessageBroker;


public class WampServices 
{
    private static final Logger logger = Logger.getLogger(MessageBroker.class.getName());
    
    private static TreeMap<String,WampApplication> apps = new TreeMap<String,WampApplication>();
    
    private static TreeMap<String,WampTopic> topics = new TreeMap<String,WampTopic>();
    
    private static TreeMap<Long,WampSubscription>   topicSubscriptionsById = new TreeMap<Long,WampSubscription>();
    private static TreeMap<String,WampSubscription> topicSubscriptionsByTopicURI = new TreeMap<String,WampSubscription>();
    
    
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
            
            for(WampSubscription subscription : topicSubscriptionsByTopicURI.values()) {
                if(isUriMatchingWithRegExp(topicFQname, subscription.getTopicRegExp())) {
                    subscription.getTopics().add(topic);

                    try { 
                        for(WampSocket socket : subscription.getSockets()) {
                            subscribeClientWithTopic(app, socket, null, topic.getURI(), subscription.getOptions());
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
                for(WampSocket client : subscription.getSockets()) {
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
    
    
    private static String getTopicRegExp(WampSubscriptionOptions.MatchEnum matchType, String topicPattern)
    {
        String regexp = (matchType==WampSubscriptionOptions.MatchEnum.prefix)? topicPattern.replace("..", ".*") : topicPattern.replace("..", "\\..+\\.");
        return regexp;
    }

    private static boolean isUriMatchingWithRegExp(String topicFQname, String regExp) 
    {
        return (topicFQname.matches(regExp));
    }

    
    public static WampTopic getTopic(String topicFQname)
    {
        WampTopic topic = topics.get(topicFQname);
        return topic;
    }  
    
    
    public static Collection<WampTopic> getTopics(WampSubscriptionOptions.MatchEnum matchType, String topicUriOrPattern)
    {
        WampSubscription subscription = topicSubscriptionsByTopicURI.get(topicUriOrPattern);
        
        if(subscription != null) {
            
            return subscription.getTopics();
            
        } else {
        
            if(matchType != WampSubscriptionOptions.MatchEnum.exact) {  // prefix or wildcards
                ArrayList<WampTopic> retval = new ArrayList<WampTopic>();
                int wildcardPos = topicUriOrPattern.indexOf("..");
                String topicUriBegin = topicUriOrPattern.substring(0, wildcardPos);
                String topicUriEnd = topicUriBegin + "~";
                NavigableMap<String,WampTopic> navMap = topics.subMap(topicUriBegin, true, topicUriEnd, false);
                String regExp = getTopicRegExp(matchType, topicUriOrPattern);
                for(WampTopic topic : navMap.values()) {
                    if(isUriMatchingWithRegExp(topic.getURI(), regExp)) {
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
    
    
    public static void processPublishMessage(WampApplication app, WampSocket clientSocket, WampList request) throws Exception 
    {
        Long publicationId = WampProtocol.newId();
        String topicName = clientSocket.normalizeURI(request.get(3).asText());
        WampTopic topic = WampServices.getTopic(topicName);
        try {
            WampModule module = app.getWampModule(topic.getBaseURI(), app.getDefaultWampModule());
            module.onPublish(publicationId, clientSocket, topic, request);
        } catch(Exception ex) {
            logger.log(Level.FINE, "Error in publishing to topic", ex);
        }  
    }
    
    
    static WampSubscription getSubscriptionById(Long subscriptionId)
    {
        return topicSubscriptionsById.get(subscriptionId);
    }    
    
    public static Collection<WampTopic> subscribeClientWithTopic(WampApplication app, WampSocket clientSocket, Long requestId, String topicUriOrPattern, WampSubscriptionOptions options)
    {
        boolean error = false;
        Long subscriptionId = null;
        
        // FIXME: merge subscriptions options (events & metaevents),
        // when the 1st eventhandler and 1st metahandler is subscribed
        topicUriOrPattern = clientSocket.normalizeURI(topicUriOrPattern);
        if(options == null) options = new WampSubscriptionOptions(null);
        if(options.getMatchType() == WampSubscriptionOptions.MatchEnum.prefix && !topicUriOrPattern.endsWith("..")) {
            topicUriOrPattern = topicUriOrPattern + "..";
        }
        
        
        WampSubscription subscription = topicSubscriptionsByTopicURI.get(topicUriOrPattern);
        if(subscription == null) {
            subscriptionId = WampProtocol.newId();  
            Collection<WampTopic> topics = WampServices.getTopics(options.getMatchType(), topicUriOrPattern);            
            String regExp = getTopicRegExp(options.getMatchType(), topicUriOrPattern);
            
            subscription = new WampSubscription(subscriptionId, regExp, topics, options);
            topicSubscriptionsById.put(subscriptionId, subscription);
            topicSubscriptionsByTopicURI.put(topicUriOrPattern, subscription);

            subscription.addSocket(clientSocket);
        }        
        
        clientSocket.addSubscription(subscription);
        for(WampTopic topic : subscription.getTopics()) {
            WampModule module = app.getWampModule(topic.getBaseURI(), app.getDefaultWampModule());
            if(subscriptionId == null) subscriptionId = topic.getSubscriptionId();
            
            try { 
                module.onSubscribe(clientSocket, subscriptionId, topic, options);
            } catch(Exception ex) {
                error = true;
            }
        }
    
        if(!error) WampProtocol.sendSubscribed(clientSocket, requestId, subscriptionId);
        else WampProtocol.sendSubscribeError(clientSocket, requestId, "wamp.error.not_authorized");
        
        return subscription.getTopics();
    }
    
    
    public static Collection<WampTopic> unsubscribeClientFromTopic(WampApplication app, WampSocket clientSocket, Long requestId, Long subscriptionId)
    {
        Collection<WampTopic> topics = null;

        WampSubscription subscription = clientSocket.getSubscription(subscriptionId);
        subscription.removeSocket(clientSocket.getSessionId());

        topics = subscription.getTopics();
        for(WampTopic topic : topics) {
            if(subscriptionId == null) subscriptionId = topic.getSubscriptionId();            
            if(subscription != null) {
                try { 
                    WampModule module = app.getWampModule(topic.getBaseURI(), app.getDefaultWampModule());
                    module.onUnsubscribe(clientSocket, subscriptionId, topic);
                } catch(Exception ex) {
                    logger.log(Level.FINE, "Error in unsubscription to topic", ex);
                }          
            }
        }
        
        if(requestId != null) WampProtocol.sendUnsubscribed(clientSocket, requestId);
                
        return topics;
    }

    
    public static void publishEvent(Long publicationId, Long publisherId, WampTopic topic, WampObject event, WampPublishOptions options) 
    {
        //logger.log(Level.FINE, "Broadcasting to {0}: {1}", new Object[]{topic.getURI(),event});
        try {
            MessageBroker.publish(publicationId, topic, event, null, options.getEligible(), options.getExcluded(), (options.hasDiscloseMe()? publisherId : null));
        } catch(Exception ex) {
            logger.log(Level.SEVERE, "Error in publishing event to topic", ex);
        }
    }   
    
    public static void publishMetaEvent(Long publicationId, WampTopic topic, String metatopic, WampObject metaevent, WampSocket toClient) 
    {
        //logger.log(Level.FINE, "Broadcasting to {0}: {1}", new Object[]{topic.getURI(),metaevent});
        try {
            HashSet<Long> eligible = new HashSet<Long>();
            if(toClient != null) eligible.add(toClient.getSessionId());
            MessageBroker.publish(publicationId, topic, metaevent, metatopic, eligible, null, null);
        } catch(Exception ex) {
            logger.log(Level.SEVERE, "Error in publishing metaevent to topic", ex);
        }        
    }    
    
}
