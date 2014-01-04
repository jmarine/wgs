package org.wgs.wamp;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.node.TextNode;


public class WampProtocol 
{
    private static Random randomizer = new Random();
    
    
    public static long newId()
    {
        synchronized(randomizer) {
            return (((long)(randomizer.nextInt(67108864)) << 27) + randomizer.nextInt(134217728));  // 53 bits
        }
    }
    
    public static void sendWelcomeMessage(WampApplication app, WampSocket clientSocket)
    {
        // Send WELCOME message to client:
        WampList response = new WampList();
        response.add(0);  // WELCOME message code
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
        
        clientSocket.sendWampMessage(response);
    }
    
    
    public static void sendCallResult(WampSocket clientSocket, int callResponseMsgType, Long callID, WampList args, WampDict argsKw)
    {
        WampList response = new WampList();
        response.add(callResponseMsgType);
        response.add(callID);
        response.add(args);
        response.add(argsKw);
        
        clientSocket.sendWampMessage(response);
    }    
    
    
    public static void sendCallError(WampSocket clientSocket, int callErrorMsgType, Long callID, String errorURI, String errorDesc, Object errorDetails)
    {
        if(errorURI == null) errorURI = WampException.ERROR_PREFIX+".call_error";
        if(errorDesc == null) errorDesc = "";

        WampList response = new WampList();
        response.add(callErrorMsgType);
        response.add(callID);
        response.add(errorURI);
        
        if(errorDetails == null) {
            response.add(errorDesc);
        } else {
            response.add(errorDesc + ": " + errorDetails.toString());
        }

        
        clientSocket.sendWampMessage(response);
    }    
    
    
    public static void sendSubscribed(WampSocket clientSocket, Long requestId, Long subscriptionId)
    {    
        WampList response = new WampList();
        response.add(11);
        response.add(requestId);
        response.add(subscriptionId);
        
        clientSocket.sendWampMessage(response);
    }
    
    public static void sendSubscribeError(WampSocket clientSocket, Long requestId, String errorUri)
    {    
        WampList response = new WampList();
        response.add(11);
        response.add(requestId);
        response.add(errorUri);
        
        clientSocket.sendWampMessage(response);
    }
    
    
    
    public static void sendUnsubscribed(WampSocket clientSocket, Long requestId)
    {    
        WampList response = new WampList();
        response.add(21);
        response.add(requestId);
        
        clientSocket.sendWampMessage(response);
    }
    
    
    public static void sendPublished(WampSocket clientSocket, Long requestId, Long publicationId)
    {    
        WampList response = new WampList();
        response.add(31);
        response.add(requestId);
        response.add(publicationId);
        
        clientSocket.sendWampMessage(response);
    }    
    
    
    public static void sendEvents(Long publicationId, WampTopic topic, Set<Long> eligibleParam, Set<Long> excluded, Long publisherId, WampObject event) throws Exception 
    {
        // EVENT data
        WampDict eventDetails = new WampDict();
        if(publisherId != null) eventDetails.put("publisher", publisherId);            
        
        for(WampSubscription subscription : topic.getSubscriptions()) {
            
            Object[] msg = new Object[WampEncoding.values().length];
            
            if(topic.getURI().equals(subscription.getTopicRegExp())) {
                eventDetails.remove("topic");
            } else {
                eventDetails.put("topic", topic.getURI());
            }
            
            WampList response = new WampList();
            response.add(40);
            response.add(subscription.getId());
            response.add(publicationId);
            response.add(eventDetails);
            response.add(event);
                                    
            Set<Long> eligible = (eligibleParam != null) ? new HashSet<Long>(eligibleParam) : null;
            if(eligible == null) eligible = subscription.getSessionIds();
            else eligible.retainAll(subscription.getSessionIds());

            if(excluded == null) excluded = new HashSet<Long>();        
            //if(excludeMe()) excluded.add(publisherId);

            for (Long sid : eligible) {
                if((excluded==null) || (!excluded.contains(sid))) {
                    WampSubscriptionOptions subOptions = subscription.getOptions();
                    if(subOptions != null && subOptions.hasEventsEnabled() && subOptions.isEligibleForEvent(sid, subscription, event)) {
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
                    remoteSocket.sendWampMessage(response);
                }
            } else {
                if(subscription.getOptions() != null && subscription.getOptions().hasMetaEvent(metaTopic)) {
                    Object[] msg = new Object[WampEncoding.values().length];
                    for(WampSocket remoteSocket : subscription.getSockets()) {
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
    
}
