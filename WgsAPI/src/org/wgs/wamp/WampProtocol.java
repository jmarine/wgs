package org.wgs.wamp;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.node.TextNode;


public class WampProtocol 
{
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
                ObjectNode features = mapper.createObjectNode();
                features.put("ProgressiveResults", 1);
                features.put("CallTimeouts", 0);
                features.put("PartitionedCalls", 0);
                features.put("CancelCalls", 1);

                ObjectNode helloDetails = mapper.createObjectNode();
                helloDetails.put("agent", "wgs");
                helloDetails.put("features", features);
                response.add(helloDetails);  
                break;
        }
        
        clientSocket.sendSafe(response.toString());        
    }
    
    
    public static void sendCallResult(WampSocket clientSocket, int callResponseMsgType, String callID, ArrayNode args)
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
        response.append(encodeJSON(callID));
        for(int i = 0; i < args.size(); i++) {
            response.append(",");
            try { 
                JsonNode obj = args.get(i); 
                if(obj instanceof TextNode) {
                    response.append(encodeJSON(obj.asText()));
                } else {
                    response.append(obj); 
                }
            }
            catch(Exception ex) { response.append("null"); }
        }
        response.append("]");
        clientSocket.sendSafe(response.toString());
    }    
    
    
    public static void sendCallError(WampSocket clientSocket, int callErrorMsgType, String callID, String errorURI, String errorDesc, Object errorDetails)
    {
        if(errorURI == null) errorURI = WampException.WAMP_GENERIC_ERROR_URI;
        if(errorDesc == null) errorDesc = "";

        StringBuilder response = new StringBuilder();
        response.append("[");
        response.append(callErrorMsgType);
        response.append(",");
        response.append(encodeJSON(callID));

        response.append(",");
        response.append(encodeJSON(errorURI));
        response.append(",");
        response.append(encodeJSON(errorDesc));
        
        if(errorDetails != null) {
            response.append(",");
            if(errorDetails instanceof String) {
                response.append(encodeJSON((String)errorDetails));
            } else {
                response.append(encodeJSON(errorDetails.toString()));
            }
        }

        response.append("]");
        
        clientSocket.sendSafe(response.toString());
    }    
    
    
    public static void sendEvents(WampTopic topic, Set<String> eligible, Set<String> excluded, String publisherId, JsonNode event) throws Exception 
    {
        // EVENT data
        String msgByVersion[] = new String[WampApplication.WAMPv2+1];  // Cache EVENT message for each WAMP version

        if(eligible == null) eligible = topic.getSessionIds();
        else eligible.retainAll(topic.getSessionIds());

        if(excluded == null) excluded = new HashSet<String>();        
        //if(excludeMe()) excluded.add(publisherId);

        for (String sid : eligible) {
            if((excluded==null) || (!excluded.contains(sid))) {
                WampSubscription subscription = topic.getSubscription(sid);
                WampSubscriptionOptions subOptions = subscription.getOptions();
                if(subOptions != null && subOptions.hasEventsEnabled() && subOptions.isEligibleForEvent(subscription, event)) {
                    WampSocket socket = subscription.getSocket();
                    synchronized(socket) {
                        if(socket != null && socket.isOpen() && !excluded.contains(sid)) {
                            if(socket.supportVersion(WampApplication.WAMPv2)) {
                                if(msgByVersion[WampApplication.WAMPv2] == null) {
                                    String eventDetails = (publisherId == null)? "" : ", { \"PUBLISHER\": \"" + publisherId + "\" }";
                                    msgByVersion[WampApplication.WAMPv2] = "[128,\"" + topic.getURI() + "\", " + event.toString() + eventDetails + "]";
                                }
                                socket.sendSafe(msgByVersion[WampApplication.WAMPv2]);
                            } else {
                                if(msgByVersion[WampApplication.WAMPv1] == null) msgByVersion[WampApplication.WAMPv1] = "[8,\"" + topic.getURI() + "\", " + event.toString() + "]";
                                socket.sendSafe(msgByVersion[WampApplication.WAMPv1]);
                            }
                        }
                    }
                }
            }
        }
    }

    
    public static void sendMetaEvents(WampTopic topic, String metaTopic, Set<String> eligible, JsonNode metaEvent) throws Exception 
    {
        // METAEVENT data (only in WAMP v2)
        String toClient = (eligible != null && eligible.size() > 0) ? eligible.iterator().next() : null;

        String msg = "[129,\"" + topic.getURI() + "\", \"" + metaTopic + "\"";
        if(metaEvent != null) msg += ", " + metaEvent.toString();
        msg += "]";

        if(toClient != null) {
            WampSubscription subscriber = topic.getSubscription(toClient);
            WampSocket remoteSocket = subscriber.getSocket();
            if(remoteSocket.supportVersion(WampApplication.WAMPv2)) remoteSocket.sendSafe(msg);
        } else {
            for(String sid : topic.getSessionIds()) {
                WampSubscription subscriber = topic.getSubscription(sid);
                if(subscriber.getOptions() != null && subscriber.getOptions().hasMetaEvent(metaTopic)) {
                    WampSocket remoteSocket = subscriber.getSocket();
                    if(remoteSocket.supportVersion(WampApplication.WAMPv2)) remoteSocket.sendSafe(msg);
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
                case '\'':
                    buffer.append("\\'");
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
