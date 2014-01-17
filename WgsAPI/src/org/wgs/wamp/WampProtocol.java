package org.wgs.wamp;

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
                WampDict roles = new WampDict();
                WampDict broker = new WampDict();
                broker.put("exclude", 1);
                broker.put("eligible", 1);
                broker.put("exclude_me", 1);
                broker.put("disclose_me", 1);
                roles.put("broker", broker);
                roles.put("dealer", new WampDict());

                WampDict helloDetails = new WampDict();
                helloDetails.put("agent", "wgs");
                helloDetails.put("roles", roles);
                response.add(helloDetails);  
                break;
        }
        
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
    
    public static void sendCallProgress(WampSocket clientSocket, Long callID, WampList args, WampDict argsKw)
    {
        WampList response = new WampList();
        response.add(72);
        response.add(callID);
        response.add(args);
        response.add(argsKw);
        
        sendWampMessage(clientSocket, response);
    }    
    
    public static void sendCallResult(WampSocket clientSocket, Long callID, WampList args, WampDict argsKw)
    {
        WampList response = new WampList();
        response.add(73);
        response.add(callID);
        response.add(args);
        response.add(argsKw);
        
        sendWampMessage(clientSocket, response);
    }    
    
    public static void sendCallError(WampSocket clientSocket, Long callID, String errorURI, String errorDesc, Object errorDetails)
    {
        if(errorURI == null) errorURI = WampException.ERROR_PREFIX+".call_error";
        if(errorDesc == null) errorDesc = "";

        WampList response = new WampList();
        response.add(74);
        response.add(callID);
        response.add(errorURI);
        
        if(errorDetails == null) {
            response.add(errorDesc);
        } else {
            response.add(errorDesc + ": " + errorDetails.toString());
        }

        
        sendWampMessage(clientSocket, response);
    }    

    
    public static void sendSubscribed(WampSocket clientSocket, Long requestId, Long subscriptionId)
    {    
        WampList response = new WampList();
        response.add(11);
        response.add(requestId);
        response.add(subscriptionId);
        
        sendWampMessage(clientSocket, response);
    }
    
    
    public static void sendSubscribeError(WampSocket clientSocket, Long requestId, String errorUri)
    {    
        WampList response = new WampList();
        response.add(12);
        response.add(requestId);
        response.add(errorUri);
        
        sendWampMessage(clientSocket, response);
    }
    
    
    public static void sendUnsubscribed(WampSocket clientSocket, Long requestId)
    {    
        WampList response = new WampList();
        response.add(21);
        response.add(requestId);
        
        sendWampMessage(clientSocket, response);
    }
    
    
    public static void sendUnsubscribeError(WampSocket clientSocket, Long requestId, String errorUri)
    {    
        WampList response = new WampList();
        response.add(22);
        response.add(requestId);
        response.add(errorUri);
        
        sendWampMessage(clientSocket, response);
    }    
    
    
    public static void sendPublished(WampSocket clientSocket, Long requestId, Long publicationId)
    {    
        WampList response = new WampList();
        response.add(31);
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
            
            if(subscription.getOptions().getMatchType() == MatchEnum.exact) {
                eventDetails.remove("topic");
            } else {
                eventDetails.put("topic", topic.getURI());
            }
            
            WampList response = new WampList();
            response.add(40);
            response.add(subscription.getId());
            response.add(publicationId);
            response.add(eventDetails);
            response.add(payload);
            response.add(payloadKw);
                                    
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

    
    public static void sendMetaEvents(Long publicationId, WampTopic topic, String metaTopic, Set<Long> eligible, WampObject metaEvent) throws Exception 
    {
        // METAEVENT data (only in WAMP v2)
        Long toClient = (eligible != null && eligible.size() > 0) ? eligible.iterator().next() : null;

        for(WampSubscription subscription : topic.getSubscriptions()) {
            
            WampList response = new WampList();
            response.add(41);
            response.add(subscription.getId());
            response.add(publicationId);
            response.add(metaTopic);
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

    
    public static void sendRegisteredMessage(WampSocket clientSocket, Long requestId, Long registrationId)
    {    
        WampList response = new WampList();
        response.add(51);
        response.add(requestId);
        response.add(registrationId);
        
        sendWampMessage(clientSocket, response);
    }      
    
    
    public static void sendRegisterError(WampSocket clientSocket, Long requestId, String errorUri)
    {    
        WampList response = new WampList();
        response.add(52);
        response.add(requestId);
        response.add(errorUri);
        
        sendWampMessage(clientSocket, response);
    }
        

    public static void sendInvocationMessage(WampSocket remotePeer, Long invocationId, Long registrationId, WampDict invocationOptions, WampList args, WampDict argsKw) 
    {
        WampList msg = new WampList();
        msg.add(80);
        msg.add(invocationId);
        msg.add(registrationId);
        msg.add(invocationOptions);
        msg.add(args);
        msg.add(argsKw); 
        
        sendWampMessage(remotePeer, msg);        
    }
    
    
    public static void sendCancelInvocation(WampSocket remotePeer, Long invocationId, WampDict cancelOptions) 
    {
        WampList msg = new WampList();
        msg.add(81);
        msg.add(invocationId);
        msg.add(cancelOptions);
        
        sendWampMessage(remotePeer, msg);        
    }
    
}
