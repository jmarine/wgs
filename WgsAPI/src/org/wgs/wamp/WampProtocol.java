package org.wgs.wamp;

import java.security.Principal;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.wgs.security.User;
import org.wgs.security.WampCRA;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampSubscriptionOptions;
import org.wgs.wamp.topic.WampTopic;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampMatchType;
import org.wgs.wamp.type.WampObject;


public class WampProtocol 
{
    private static final Logger logger = Logger.getLogger(WampProtocol.class.toString());    
    private static Random randomizer = new Random();

    public static final int HELLO = 1;
    public static final int WELCOME = 2;
    public static final int ABORT = 3;
    public static final int CHALLENGE = 4;
    public static final int AUTHENTICATE = 5;
    public static final int GOODBYE = 6;
    public static final int HEARTBEAT = 7;    
    public static final int ERROR = 8;
    public static final int PUBLISH = 16;
    public static final int PUBLISHED = 17;
    public static final int SUBSCRIBE = 32;
    public static final int SUBSCRIBED = 33;
    public static final int UNSUBSCRIBE = 34;
    public static final int UNSUBSCRIBED = 35;
    public static final int EVENT = 36;
    public static final int METAEVENT = 36;
    public static final int REGISTER = 64;
    public static final int REGISTERED = 65;
    public static final int UNREGISTER = 66;
    public static final int UNREGISTERED = 67;
    public static final int CALL = 48;
    public static final int CANCEL_CALL = 49;
    public static final int CALL_RESULT = 50;
    public static final int INVOCATION = 68;
    public static final int INTERRUPT = 69;     // CANCEL INVOCATION
    public static final int YIELD = 70;         // INVOCATION RESULT
    
    
    public static long newId()
    {
        synchronized(randomizer) {
            return (((long)(randomizer.nextInt(67108864)) << 27) + randomizer.nextInt(134217728));  // 53 bits
        }
    }
    

    private static void sendWampMessage(WampSocket socket, WampList args)
    {
        try {        
            if(logger.isLoggable(Level.FINE)) logger.log(Level.FINE, "sendWampMessage: " + args);
            Object msg = socket.getEncoding().getSerializer().serialize(args);
            socket.sendObject(msg);

        } catch(Exception ex) {
            logger.log(Level.FINE, "Serialization error '" + ex.getClass().getName() + "': " + ex.getMessage(), ex);
            ex.printStackTrace();
        }

    }    
    
    public static void sendHelloMessage(WampSocket clientSocket, String realm, WampDict options)
    {
        WampList response = new WampList();
        response.add(HELLO);
        response.add(realm);
        if(options != null) response.add(options);
        WampProtocol.sendWampMessage(clientSocket, response);
    }
    
    
    public static void sendWelcomeMessage(WampApplication app, WampSocket clientSocket)
    {
        // Send WELCOME message to client:
        WampList response = new WampList();
        response.add(WELCOME);
        response.add(clientSocket.getSessionId());

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
        dealerFeatures.put("callee_blackwhite_listing", true);
        dealerFeatures.put("caller_exclusion", true);
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
        
        WampDict details = new WampDict();
        details.put("agent", app.getServerId());
        details.put("roles", roles);

        details.put("authid", clientSocket.getAuthId());
        details.put("authmethod", clientSocket.getAuthMethod());
        details.put("authrole", clientSocket.getAuthRole());
        details.put("authprovider", clientSocket.getAuthProvider());
        
        response.add(details);  
        
        sendWampMessage(clientSocket, response);
    }
    
    public static void sendChallengeMessage(WampSocket clientSocket, String authMethod, WampDict extra)
    {
        if(extra == null) extra = new WampDict();
        
        clientSocket.setAuthMethod(authMethod);
        
        WampList response = new WampList();
        response.add(CHALLENGE);
        response.add(authMethod);
        response.add(extra);
        sendWampMessage(clientSocket, response);
    }
    
    public static void sendAuthenticationMessage(WampSocket clientSocket, String signature, WampDict extra) 
    {
        if(extra == null || extra.size() == 0) extra = new WampDict();
        WampList response = new WampList();
        response.add(AUTHENTICATE);
        response.add(signature);
        response.add(extra);
        sendWampMessage(clientSocket, response);
    }

    public static void sendAbortMessage(WampSocket clientSocket, String reason, String message)
    {
        WampDict details = new WampDict();
        if(message != null) details.put("message", message);
        WampList response = new WampList();
        response.add(ABORT);
        response.add(details);
        response.add(reason);
        sendWampMessage(clientSocket, response);
    }
    
    public static void sendGoodbyeMessage(WampSocket clientSocket, String reason, String message)
    {
        if(!clientSocket.isGoodbyeRequested()) {
            clientSocket.setGoodbyeRequested(true);
            WampDict details = new WampDict();
            if(message != null) details.put("message", message);
            WampList response = new WampList();
            response.add(GOODBYE);
            response.add(details);
            response.add(reason);
            sendWampMessage(clientSocket, response);
        }
    }
    
    public static void sendHeartbeatMessage(WampSocket clientSocket, String discard)
    {
        WampList response = new WampList();
        response.add(HEARTBEAT);
        response.add(clientSocket.getIncomingHeartbeat());
        response.add(clientSocket.getNextOutgoingHeartbeatSeq());
        response.add(discard);
        sendWampMessage(clientSocket, response);
    }
    
    
    public static void sendResultMessage(WampSocket clientSocket, Long requestId, WampDict details, WampList args, WampDict argsKw)
    {
        WampList response = new WampList();
        response.add(CALL_RESULT);
        response.add(requestId);
        response.add((details != null)? details : new WampDict());
        if( (args != null && args.size() > 0) || (argsKw != null && argsKw.size() > 0) ) {
            response.add((args != null)? args : new WampList());
            if(argsKw != null && argsKw.size() > 0) response.add(argsKw);
        }
        sendWampMessage(clientSocket, response);
    }    
    
    
    public static void sendSubscribeMessage(WampSocket clientSocket, Long requestId, String topicURI, WampSubscriptionOptions options)
    {
        WampList response = new WampList();
        response.add(SUBSCRIBE);
        response.add(requestId);
        response.add((options != null)? options.toWampObject() : new WampSubscriptionOptions(null));
        response.add(topicURI);
        sendWampMessage(clientSocket, response);
    }
    
    public static void sendSubscribedMessage(WampSocket clientSocket, Long requestId, Long subscriptionId)
    {    
        WampList response = new WampList();
        response.add(SUBSCRIBED);
        response.add(requestId);
        response.add(subscriptionId);
        sendWampMessage(clientSocket, response);
    }
    
    
    public static void sendUnsubscribeMessage(WampSocket clientSocket, Long requestId, Long unsubscriptionId)
    {
        WampList response = new WampList();
        response.add(UNSUBSCRIBE);
        response.add(requestId);
        response.add(unsubscriptionId);
        sendWampMessage(clientSocket, response);
    }
    
    
    public static void sendUnsubscribedMessage(WampSocket clientSocket, Long requestId)
    {    
        WampList response = new WampList();
        response.add(UNSUBSCRIBED);
        response.add(requestId);
        sendWampMessage(clientSocket, response);
    }
    

    public static void sendPublishMessage(WampSocket clientSocket, Long requestId, String topic, WampList payload, WampDict payloadKw, WampDict options)
    {
        WampList request = new WampList();
        request.add(PUBLISH);
        request.add(requestId);
        request.add( (options != null) ? options : new WampDict() );      
        request.add(topic);
        request.add(payload);
        request.add(payloadKw);
        sendWampMessage(clientSocket, request);
    }
    
    
    public static void sendPublishedMessage(WampSocket clientSocket, Long requestId, Long publicationId)
    {    
        WampList response = new WampList();
        response.add(PUBLISHED);
        response.add(requestId);
        response.add(publicationId);
        sendWampMessage(clientSocket, response);
    }    
    
    
    public static void sendEvents(String realm, Long publicationId, WampTopic topic, WampList payload, WampDict payloadKw, Set<Long> eligibleParam, Set<Long> excluded, Long publisherId, String publisherAuthId, String publisherAuthProvider, String publisherAuthRole) throws Exception 
    {
        // EVENT data
        WampDict eventDetails = new WampDict();
        if(publisherId != null) eventDetails.put("publisher", publisherId);            
        if(publisherAuthId != null) eventDetails.put("authid", publisherAuthId);
        if(publisherAuthProvider != null) eventDetails.put("authprovider", publisherAuthProvider);
        if(publisherAuthRole != null) eventDetails.put("authrole", publisherAuthRole);
        
        for(WampSubscription subscription : topic.getSubscriptions()) {
            
            Object[] msg = new Object[WampEncoding.values().length];
            
            if(subscription.getOptions().getMatchType() == WampMatchType.exact) {
                eventDetails.remove("topic");
            } else {
                eventDetails.put("topic", topic.getTopicName());
            }
            
            WampList response = new WampList();
            response.add(EVENT);
            response.add(subscription.getId());
            response.add(publicationId);
            response.add(eventDetails);
            if( (payload != null && payload.size() > 0) || (payloadKw != null && payloadKw.size() > 0) ) {
                response.add((payload!=null) ? payload : new WampList());
                if(payloadKw != null && payloadKw.size() > 0) response.add(payloadKw);
            }
                                    
            Set<Long> eligible = (eligibleParam != null) ? new HashSet<Long>(eligibleParam) : null;
            if(eligible == null) eligible = subscription.getSessionIds(realm);
            else eligible.retainAll(subscription.getSessionIds(realm));

            
            if(excluded == null) excluded = new HashSet<Long>();        

            for (Long sid : eligible) {
                if(!excluded.contains(sid)) {
                    WampSubscriptionOptions subOptions = subscription.getOptions();
                    if(subOptions != null && subOptions.hasEventsEnabled() && subOptions.isEligibleForEvent(sid, subscription, payload, payloadKw)) {
                        WampSocket socket = subscription.getSocket(sid);
                        synchronized(socket) {
                            if(socket != null && socket.isOpen() && realm.equals(socket.getRealm()) ) {
                                WampEncoding enc = socket.getEncoding();
                                if(msg[enc.ordinal()] == null) {
                                    msg[enc.ordinal()] = enc.getSerializer().serialize(response);
                                }
                                socket.sendObject(msg[enc.ordinal()]);
                            }
                        }
                    }
                }
            }
        }
    }

    
    public static void sendMetaEvents(String realm, Long publicationId, WampTopic topic, String metaTopic, Set<Long> eligible, WampDict metaEvent) throws Exception 
    {
        // METAEVENT data (only in WAMP v2)
        metaEvent.put("metatopic", metaTopic);
        Long toClient = (eligible != null && eligible.size() > 0) ? eligible.iterator().next() : null;

        for(WampSubscription subscription : topic.getSubscriptions()) {
            
            if(subscription.getOptions().getMatchType() == WampMatchType.exact) {
                metaEvent.remove("topic");
            } else {
                metaEvent.put("topic", topic.getTopicName());
            }            
            
            WampList response = new WampList();
            response.add(METAEVENT);
            response.add(subscription.getId());
            response.add(publicationId);
            response.add(metaEvent);
            
            if(toClient != null) {
                WampSocket remoteSocket = subscription.getSocket(toClient);
                if(remoteSocket != null && realm.equals(remoteSocket.getRealm())) {
                    sendWampMessage(remoteSocket, response);
                }
            } else {
                if(subscription.getOptions() != null && subscription.getOptions().hasMetaTopic(metaTopic)) {
                    Object[] msg = new Object[WampEncoding.values().length];
                    for(Long sid : subscription.getSessionIds(realm)) {  // FIXME: concurrent modification exceptions
                        WampSocket remoteSocket = subscription.getSocket(sid);
                        if(realm.equals(remoteSocket.getRealm()) && remoteSocket.supportVersion(WampApplication.WAMPv2)) {
                            WampEncoding enc = remoteSocket.getEncoding();
                            if(msg[enc.ordinal()] == null) {
                                msg[enc.ordinal()] = enc.getSerializer().serialize(response);
                            }
                            remoteSocket.sendObject(msg[enc.ordinal()]);
                        }
                    }
                }
            }
        }

    }

    
    public static void sendErrorMessage(WampSocket clientSocket, int requestType, Long requestId, WampDict details, String errorUri, WampList args, WampDict argsKw)
    {    
        WampList response = new WampList();
        response.add(ERROR);
        response.add(requestType);
        response.add(requestId);
        response.add((details != null)? details : new WampDict());
        response.add(errorUri);
        if(args != null || argsKw != null) {
            response.add((args != null)? args : new WampList());
            if(argsKw != null) response.add(argsKw);
        }
        sendWampMessage(clientSocket, response);
    }    
    
    
    public static void sendCallMessage(WampSocket clientSocket, Long requestId, WampDict options, String procedureURI, WampList args, WampDict argsKw)
    {
        WampList response = new WampList();
        response.add(CALL);
        response.add(requestId);
        response.add((options != null)? options : new WampDict());
        response.add(procedureURI);
        if(args != null) response.add(args);
        if(argsKw != null && argsKw.size() > 0) response.add(argsKw);
        sendWampMessage(clientSocket, response);
    }
    
    public static void sendCancelCallMessage(WampSocket clientSocket, Long callId, WampDict options)
    {
        WampList response = new WampList();
        response.add(CANCEL_CALL);
        response.add(callId);
        response.add((options != null)? options : new WampDict());
        sendWampMessage(clientSocket, response);
    }
    
    public static void sendRegisterMessage(WampSocket clientSocket, Long requestId, WampDict options, String procedureURI)
    {
        WampList response = new WampList();
        response.add(REGISTER);
        response.add(requestId);
        response.add((options != null)? options : new WampDict());
        response.add(procedureURI);
        sendWampMessage(clientSocket, response);        
    }
    
    public static void sendRegisteredMessage(WampSocket clientSocket, Long requestId, Long registrationId)
    {    
        WampList response = new WampList();
        response.add(REGISTERED);
        response.add(requestId);
        response.add(registrationId);
        sendWampMessage(clientSocket, response);
    }      
    
    public static void sendUnregisterMessage(WampSocket clientSocket, Long requestId, Long registrationId)
    {
        WampList response = new WampList();
        response.add(UNREGISTER);
        response.add(requestId);
        response.add(registrationId);
        sendWampMessage(clientSocket, response);        
    }    
        
    public static void sendUnregisteredMessage(WampSocket clientSocket, Long requestId)
    {    
        WampList response = new WampList();
        response.add(UNREGISTERED);
        response.add(requestId);
        sendWampMessage(clientSocket, response);
    }      

    
    public static void sendInvocationMessage(WampSocket remotePeer, Long invocationId, Long registrationId, WampDict details, WampList args, WampDict argsKw) 
    {
        WampList msg = new WampList();
        msg.add(INVOCATION);
        msg.add(invocationId);
        msg.add(registrationId);
        msg.add(details);
        if( (args != null && args.size() > 0) || (argsKw != null && argsKw.size() > 0) ) {
            msg.add((args!=null)? args : new WampList());
            if(argsKw != null && argsKw.size() > 0) msg.add(argsKw); 
        }
        sendWampMessage(remotePeer, msg);        
    }
    
    
    public static void sendInvocationResultMessage(WampSocket clientSocket, Long invocationRequestId, WampDict invocationResultOptions, WampList result, WampDict resultKw) 
    {
        WampList msg = new WampList();
        msg.add(YIELD);
        msg.add(invocationRequestId);
        msg.add(invocationResultOptions);
        msg.add(result);
        if(resultKw != null && resultKw.size() > 0) msg.add(resultKw);
        sendWampMessage(clientSocket, msg);        
    }
    
    public static void sendInterruptMessage(WampSocket remotePeer, Long invocationId, WampDict cancelOptions) 
    {
        if(cancelOptions == null) cancelOptions = new WampDict();
        
        WampList msg = new WampList();
        msg.add(INTERRUPT);
        msg.add(invocationId);
        msg.add(cancelOptions);
        sendWampMessage(remotePeer, msg);        
    }
    
}
