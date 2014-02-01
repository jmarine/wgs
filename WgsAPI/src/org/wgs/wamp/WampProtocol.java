package org.wgs.wamp;

import org.wgs.wamp.types.WampMatchType;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.types.WampDict;
import org.wgs.wamp.types.WampObject;
import org.wgs.wamp.types.WampList;
import org.wgs.wamp.topic.WampTopic;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampSubscriptionOptions;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


public class WampProtocol 
{
    private static final Logger logger = Logger.getLogger(WampProtocol.class.toString());    
    private static Random randomizer = new Random();
    
    
    public static long newId()
    {
        synchronized(randomizer) {
            return (((long)(randomizer.nextInt(67108864)) << 27) + randomizer.nextInt(134217728));  // 53 bits
        }
    }
    

    private static void sendWampMessage(WampSocket socket, WampList args)
    {
        try {        
            Object msg = WampObject.getSerializer(socket.getEncoding()).serialize(args);
            socket.sendObject(msg);

        } catch(Exception ex) {
            logger.log(Level.FINE, "Serialization error '" + ex.getClass().getName() + "': " + ex.getMessage(), ex);
            ex.printStackTrace();
        }

    }    
    
    
    public static void sendWelcomeMessage(WampApplication app, WampSocket clientSocket)
    {
        // Send WELCOME message to client:
        WampList response = new WampList();
        response.add(1);  // WELCOME message code
        response.add(clientSocket.getSessionId());
        switch(app.getWampVersion()) {
            case WampApplication.WAMPv1:
                response.add(1);  // WAMP v1
                response.add(app.getServerId());
                break;
            case WampApplication.WAMPv2:
                WampDict brokerFeatures = new WampDict();
                brokerFeatures.put("subscriber_blackwhite_listing", true);
                brokerFeatures.put("publisher_exclusion", true);
                brokerFeatures.put("publisher_identification", true);
                //brokerFeatures.put("publication_trustlevels", false);
                brokerFeatures.put("pattern_based_subscription", true);
                brokerFeatures.put("partitioned_pubsub", true);
                brokerFeatures.put("subscriber_metaevents", true);
                //brokerFeatures.put("subscriber_list", false);
                //brokerFeatures.put("event_history", false);

                WampDict dealerFeatures = new WampDict();
                //dealerFeatures.put("callee_blackwhite_listing", false);
                //dealerFeatures.put("caller_exclusion", false);
                dealerFeatures.put("caller_identification", true);
                //dealerFeatures.put("call_trustlevels", false);
                dealerFeatures.put("pattern_based_registration", true);
                dealerFeatures.put("partitioned_rpc", true);
                //dealerFeatures.put("call_timeout", false);
                dealerFeatures.put("call_canceling", true);
                dealerFeatures.put("progressive_call_results", true);
                
                WampDict broker = new WampDict();
                broker.put("features", brokerFeatures);
                WampDict dealer = new WampDict();
                dealer.put("features", dealerFeatures);
                WampDict roles = new WampDict();
                roles.put("broker", broker);
                roles.put("dealer", dealerFeatures);

                WampDict helloDetails = new WampDict();
                helloDetails.put("agent", app.getServerId());
                helloDetails.put("roles", roles);
                response.add(helloDetails);  
                break;
        }
        
        sendWampMessage(clientSocket, response);
    }
    
    public static void sendGoodBye(WampSocket clientSocket, String reason, String message)
    {
        WampDict details = new WampDict();
        if(reason != null) details.put("reason", reason);
        if(message != null) details.put("message", message);
        WampList response = new WampList();
        response.add(2);
        response.add(details);
        sendWampMessage(clientSocket, response);
    }
    
    public static void sendHeartbeatMessage(WampSocket clientSocket, String discard)
    {
        WampList response = new WampList();
        response.add(3);
        response.add(clientSocket.getIncomingHeartbeat());
        response.add(clientSocket.getNextOutgoingHeartbeatSeq());
        response.add(discard);
        sendWampMessage(clientSocket, response);
    }
    
    
    public static void sendResult(WampSocket clientSocket, Long requestId, WampDict details, WampList args, WampDict argsKw)
    {
        WampList response = new WampList();
        response.add(50);
        response.add(requestId);
        response.add((details != null)? details : new WampDict());
        if(args != null || argsKw != null) {
            response.add((args != null)? args : new WampList());
            if(argsKw != null) response.add(argsKw);
        }
        sendWampMessage(clientSocket, response);
    }    
    
    
    public static void sendSubscribed(WampSocket clientSocket, Long requestId, Long subscriptionId)
    {    
        WampList response = new WampList();
        response.add(33);
        response.add(requestId);
        response.add(subscriptionId);
        sendWampMessage(clientSocket, response);
    }
    
    
    public static void sendUnsubscribed(WampSocket clientSocket, Long requestId)
    {    
        WampList response = new WampList();
        response.add(35);
        response.add(requestId);
        sendWampMessage(clientSocket, response);
    }
    
    
    public static void sendPublished(WampSocket clientSocket, Long requestId, Long publicationId)
    {    
        WampList response = new WampList();
        response.add(17);
        response.add(requestId);
        response.add(publicationId);
        sendWampMessage(clientSocket, response);
    }    
    
    
    public static void sendEvents(Long publicationId, WampTopic topic, Set<Long> eligibleParam, Set<Long> excluded, Long publisherId, WampList payload, WampDict payloadKw) throws Exception 
    {
        // EVENT data
        WampDict eventDetails = new WampDict();
        if(publisherId != null) eventDetails.put("publisher", publisherId);            
        
        for(WampSubscription subscription : topic.getSubscriptions()) {
            
            Object[] msg = new Object[WampEncoding.values().length];
            
            if(subscription.getOptions().getMatchType() == WampMatchType.exact) {
                eventDetails.remove("topic");
            } else {
                eventDetails.put("topic", topic.getURI());
            }
            
            WampList response = new WampList();
            response.add(36);
            response.add(subscription.getId());
            response.add(publicationId);
            response.add(eventDetails);
            if(payload != null || payloadKw != null) {
                response.add((payload!=null) ? payload : new WampList());
                if(payloadKw != null) response.add(payloadKw);
            }
                                    
            Set<Long> eligible = (eligibleParam != null) ? new HashSet<Long>(eligibleParam) : null;
            if(eligible == null) eligible = subscription.getSessionIds();
            else eligible.retainAll(subscription.getSessionIds());

            if(excluded == null) excluded = new HashSet<Long>();        
            //if(excludeMe()) excluded.add(publisherId);

            for (Long sid : eligible) {
                if((excluded==null) || (!excluded.contains(sid))) {
                    WampSubscriptionOptions subOptions = subscription.getOptions();
                    if(subOptions != null && subOptions.hasEventsEnabled() && subOptions.isEligibleForEvent(sid, subscription, payload, payloadKw)) {
                        WampSocket socket = subscription.getSocket(sid);
                        synchronized(socket) {
                            if(socket != null && socket.isOpen() && !excluded.contains(sid)) {
                                WampEncoding enc = socket.getEncoding();
                                if(msg[enc.ordinal()] == null) {
                                    msg[enc.ordinal()] = WampObject.getSerializer(enc).serialize(response);
                                }
                                socket.sendObject(msg[enc.ordinal()]);
                            }
                        }
                    }
                }
            }
        }
    }

    
    public static void sendMetaEvents(Long publicationId, WampTopic topic, String metaTopic, Set<Long> eligible, WampDict metaEvent) throws Exception 
    {
        // METAEVENT data (only in WAMP v2)
        metaEvent.put("metatopic", metaTopic);
        Long toClient = (eligible != null && eligible.size() > 0) ? eligible.iterator().next() : null;

        for(WampSubscription subscription : topic.getSubscriptions()) {
            
            if(subscription.getOptions().getMatchType() == WampMatchType.exact) {
                metaEvent.remove("topic");
            } else {
                metaEvent.put("topic", topic.getURI());
            }            
            
            WampList response = new WampList();
            response.add(36);
            response.add(subscription.getId());
            response.add(publicationId);
            response.add(metaEvent);
            
            if(toClient != null) {
                WampSocket remoteSocket = subscription.getSocket(toClient);
                if(remoteSocket != null) {
                    sendWampMessage(remoteSocket, response);
                }
            } else {
                if(subscription.getOptions() != null && subscription.getOptions().hasMetaTopic(metaTopic)) {
                    Object[] msg = new Object[WampEncoding.values().length];
                    for(Long sid : subscription.getSessionIds()) {  // FIXME: concurrent modification exceptions
                        WampSocket remoteSocket = subscription.getSocket(sid);
                        if(remoteSocket.supportVersion(WampApplication.WAMPv2)) {
                            WampEncoding enc = remoteSocket.getEncoding();
                            if(msg[enc.ordinal()] == null) {
                                msg[enc.ordinal()] = WampObject.getSerializer(enc).serialize(response);
                            }
                            remoteSocket.sendObject(msg[enc.ordinal()]);
                        }
                    }
                }
            }
        }

    }

    
    public static void sendError(WampSocket clientSocket, Long requestId, WampDict details, String errorUri, WampList args, WampDict argsKw)
    {    
        WampList response = new WampList();
        response.add(4);
        response.add(requestId);
        response.add((details != null)? details : new WampDict());
        response.add(errorUri);
        if(args != null || argsKw != null) {
            response.add((args != null)? args : new WampList());
            if(argsKw != null) response.add(argsKw);
        }
        sendWampMessage(clientSocket, response);
    }    
    
    public static void sendRegisteredMessage(WampSocket clientSocket, Long requestId, Long registrationId)
    {    
        WampList response = new WampList();
        response.add(65);
        response.add(requestId);
        response.add(registrationId);
        sendWampMessage(clientSocket, response);
    }      
        
    public static void sendUnregisteredMessage(WampSocket clientSocket, Long requestId)
    {    
        WampList response = new WampList();
        response.add(67);
        response.add(requestId);
        sendWampMessage(clientSocket, response);
    }      

    
    public static void sendInvocationMessage(WampSocket remotePeer, Long invocationId, Long registrationId, WampDict details, WampList args, WampDict argsKw) 
    {
        WampList msg = new WampList();
        msg.add(68);
        msg.add(invocationId);
        msg.add(registrationId);
        msg.add(details);
        if(args != null || argsKw != null) {
            msg.add((args!=null)? args : new WampList());
            if(argsKw != null) msg.add(argsKw); 
        }
        sendWampMessage(remotePeer, msg);        
    }
    
    
    public static void sendInterruptMessage(WampSocket remotePeer, Long invocationId, WampDict cancelOptions) 
    {
        WampList msg = new WampList();
        msg.add(81);
        msg.add(invocationId);
        msg.add(cancelOptions);
        sendWampMessage(remotePeer, msg);        
    }
    
}
