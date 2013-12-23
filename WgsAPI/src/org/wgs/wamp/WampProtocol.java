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
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode response = mapper.createArrayNode();
        response.add(0);  // WELCOME message code
        response.add(clientSocket.getSessionId());
        switch(app.getWampVersion()) {
            case WampApplication.WAMPv1:
                response.add(1);  // WAMP v1
                response.add(app.getServerId());
                break;
            case WampApplication.WAMPv2:
                ObjectNode roles = mapper.createObjectNode();
                ObjectNode broker = mapper.createObjectNode();
                broker.put("exclude", 1);
                broker.put("eligible", 1);
                broker.put("exclude_me", 1);
                broker.put("disclose_me", 1);
                roles.put("broker", broker);
                roles.put("dealer", mapper.createObjectNode());

                ObjectNode helloDetails = mapper.createObjectNode();
                helloDetails.put("agent", "wgs");
                helloDetails.put("roles", roles);
                response.add(helloDetails);  
                break;
        }
        
        clientSocket.sendSafe(response.toString());        
    }
    
    
    public static void sendCallResult(WampSocket clientSocket, int callResponseMsgType, Long callID, ArrayNode args, ObjectNode argsKw)
    {
        StringBuilder response = new StringBuilder();
        if(args == null) {
            ObjectMapper mapper = new ObjectMapper();
            args = mapper.createArrayNode();
            args.add((String)null);
        }

        response.append("[");
        response.append(callResponseMsgType);
        response.append(",");
        response.append(callID);
        response.append(",");
        response.append(args);
        response.append(",");
        response.append(argsKw);
        response.append("]");
        clientSocket.sendSafe(response.toString());
    }    
    
    
    public static void sendCallError(WampSocket clientSocket, int callErrorMsgType, Long callID, String errorURI, String errorDesc, Object errorDetails)
    {
        if(errorURI == null) errorURI = WampException.WAMP_GENERIC_ERROR_URI;
        if(errorDesc == null) errorDesc = "";

        StringBuilder response = new StringBuilder();
        response.append("[");
        response.append(callErrorMsgType);
        response.append(",");
        response.append(callID);

        response.append(",");
        response.append(encodeJSON(errorURI));
        response.append(",");
        
        if(errorDetails == null) {
            response.append(encodeJSON(errorDesc));
        } else {
            response.append(encodeJSON(errorDesc + ": " + errorDetails.toString()));
        }

        response.append("]");
        
        clientSocket.sendSafe(response.toString());
    }    
    
    
    public static void sendSubscribed(WampSocket clientSocket, Long requestId, Long subscriptionId)
    {    
        StringBuilder response = new StringBuilder();
        response.append("[");
        response.append(11);
        response.append(",");
        response.append(requestId);
        response.append(",");
        response.append(subscriptionId);
        response.append("]");
        
        clientSocket.sendSafe(response.toString());
    }
    
    
    public static void sendUnsubscribed(WampSocket clientSocket, Long requestId)
    {    
        StringBuilder response = new StringBuilder();
        response.append("[");
        response.append(21);
        response.append(",");
        response.append(requestId);
        response.append("]");
        
        clientSocket.sendSafe(response.toString());
    }
    
    
    public static void sendPublished(WampSocket clientSocket, Long requestId, Long publicationId)
    {    
        StringBuilder response = new StringBuilder();
        response.append("[");
        response.append(31);
        response.append(",");
        response.append(requestId);
        response.append(",");
        response.append(publicationId);
        response.append("]");
        
        clientSocket.sendSafe(response.toString());
    }    
    
    
    public static void sendEvents(Long publicationId, WampTopic topic, Set<Long> eligibleParam, Set<Long> excluded, Long publisherId, JsonNode event) throws Exception 
    {
        // EVENT data
        for(WampSubscription subscription : topic.getSubscriptions()) {
            String msg = null;
           
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
                                if(msg == null) {
                                    String eventDetails = (publisherId == null)? "{}" : "{ \"PUBLISHER\": \"" + publisherId + "\" }";
                                    msg = "[40,"+subscription.getId()+"," + publicationId + "," + eventDetails + ",\"" + topic.getURI() + "\", " + event.toString() + "]";
                                }
                                socket.sendSafe(msg);
                            }
                        }
                    }
                }
            }
        }
    }

    
    public static void sendMetaEvents(Long publicationId, WampTopic topic, String metaTopic, Set<Long> eligible, JsonNode metaEvent) throws Exception 
    {
        // METAEVENT data (only in WAMP v2)
        Long toClient = (eligible != null && eligible.size() > 0) ? eligible.iterator().next() : null;

        for(WampSubscription subscription : topic.getSubscriptions()) {
            String msg = "[41,\"" + topic.getURI() + "\", \"" + metaTopic + "\"";
            if(metaEvent != null) msg += ", " + metaEvent.toString();
            msg += "]";

            if(toClient != null) {
                WampSocket remoteSocket = subscription.getSocket(toClient);
                if(remoteSocket != null) remoteSocket.sendSafe(msg);
            } else {
                if(subscription.getOptions() != null && subscription.getOptions().hasMetaEvent(metaTopic)) {
                    for(WampSocket remoteSocket : subscription.getSockets()) {
                        if(remoteSocket.supportVersion(WampApplication.WAMPv2)) remoteSocket.sendSafe(msg);
                    }
                }
            }
        }

    }

    
    private static String encodeJSON(String orig) 
    {
        if(orig == null) return "null";
        
        StringBuilder buffer = new StringBuilder(orig.length());
        buffer.append("\"");

        for (int i = 0; i < orig.length(); i++) {
            char c = orig.charAt(i);
            switch (c) {
                case '\b':
                    buffer.append("\\b");
                    break;
                case '\f':
                    buffer.append("\\f");
                    break;
                case '\n':
                    buffer.append("<br />");
                    break;
                case '\r':
                    // ignore
                    break;
                case '\t':
                    buffer.append("\\t");
                    break;
                case '\"':
                    buffer.append("\\\"");
                    break;
                case '\\':
                    buffer.append("\\\\");
                    break;
                default:
                    buffer.append(c);
            }
        }
        buffer.append("\"");
        return buffer.toString();
    }    
    
}
