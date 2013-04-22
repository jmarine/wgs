package org.wampservices;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.NullNode;
import org.codehaus.jackson.node.ObjectNode;



public abstract class WampModule 
{
    private WampApplication app;
    private HashMap<String,Method> rpcs;
    
    public WampModule(WampApplication app) {
        this.app = app;

        rpcs = new HashMap<String,Method>();
        for(Method method : this.getClass().getMethods()) {
            WampRPC rpc = method.getAnnotation(WampRPC.class);
            if(rpc != null) {
                String name = rpc.name();
                if(name.length() == 0) name = method.getName();
                rpcs.put(name, method);
            }
        }
        
    }
    
    public WampApplication getWampApplication()
    {
        return app;
    }
    
    public abstract String getBaseURL();
    
    public void onConnect(WampSocket clientSocket) throws Exception { }
    
    public void onDisconnect(WampSocket clientSocket) throws Exception { }

    @SuppressWarnings("unchecked")
    public Object onCall(WampSocket clientSocket, String methodName, ArrayNode args, WampCallOptions options) throws Exception 
    {
        ObjectMapper mapper = new ObjectMapper();
        Method method = rpcs.get(methodName);
        if(method != null) {
            int argCount = 0;
            ArrayList params = new ArrayList();
            for(Class paramType : method.getParameterTypes()) {
                if(paramType.isInstance(clientSocket)) {  // WampSocket parameter info
                    params.add(clientSocket);
                } else if(paramType.isInstance(app)) {    // WampApplication parameter info
                    params.add(app);
                } else if(WampCallOptions.class.isAssignableFrom(paramType)) {
                    params.add(options);
                } else if(ArrayNode.class.isAssignableFrom(paramType)) {
                    params.add(args);  // TODO: only from argCount to args.size()
                    argCount = args.size();
                } else {
                    JsonNode val = args.get(argCount++);
                    if(val instanceof NullNode) params.add(null);
                    else params.add(mapper.readValue(val, paramType));
                }
            }
            return method.invoke(this, params.toArray());
        }

        throw new WampException(WampException.WAMP_GENERIC_ERROR_URI, "Method not implemented: " + methodName);
    }
    
    public void onSubscribe(WampSocket clientSocket, WampTopic topic, WampSubscriptionOptions options) throws Exception { 
        WampSubscription subscription = topic.getSubscription(clientSocket.getSessionId());
        if(subscription == null) subscription = new WampSubscription(clientSocket, topic.getURI(), options);
        if(subscription.refCount(+1) == 1) {
            topic.addSubscription(subscription);
            clientSocket.addSubscription(subscription);
            if(options != null && options.hasMetaEventsEnabled()) {
                app.publishMetaEvent(topic, WampMetaTopic.OK, null, clientSocket);
                
                if(options.hasEventsEnabled()) {
                    app.publishMetaEvent(topic, WampMetaTopic.JOINED, subscription.toJSON(), null);
                }
            }
        }         
    }

    public void onUnsubscribe(WampSocket clientSocket, WampTopic topic) throws Exception { 
        WampSubscription subscription = topic.getSubscription(clientSocket.getSessionId());
        if(subscription.refCount(-1) <= 0) {
            WampSubscriptionOptions options = subscription.getOptions();
            if(options!=null && options.hasMetaEventsEnabled() && options.hasEventsEnabled()) {
                ObjectNode metaevent = subscription.toJSON();
                app.publishMetaEvent(topic, WampMetaTopic.LEFT, metaevent, null);
            }
            topic.removeSubscription(subscription.getSocket().getSessionId());
            clientSocket.removeSubscription(subscription.getTopicUriOrPattern());
        }
    }
    
    public void onPublish(WampSocket clientSocket, WampTopic topic, ArrayNode request) throws Exception 
    {
        WampPublishOptions options = new WampPublishOptions();
        JsonNode event = request.get(2);
        
        if(request.get(0).asInt() == 66) {
            // WAMP v2
            options.init(request.get(3));
        } else {
            // WAMP v1
            if(request.size() == 4) {
                // Argument 4 could be a BOOLEAN(excludeMe) or JSONArray(excludedIds)
                try {
                    boolean excludeMe = request.get(3).asBoolean();
                    options.setExcludeMe(excludeMe);
                } catch(Exception ex) {
                    HashSet<String> excludedSet = new HashSet<String>();
                    ArrayNode excludedArray = (ArrayNode)request.get(3);
                    for(int i = 0; i < excludedArray.size(); i++) {
                        excludedSet.add(excludedArray.get(i).asText());
                    }
                    options.setExcluded(excludedSet);
                }
            } else if(request.size() == 5) {
                HashSet<String> excludedSet = new HashSet<String>();
                HashSet<String> eligibleSet = new HashSet<String>();
                ArrayNode excludedArray = (ArrayNode)request.get(3);
                for(int i = 0; i < excludedArray.size(); i++) {
                    excludedSet.add(excludedArray.get(i).asText());
                }
                ArrayNode eligibleArray = (ArrayNode)request.get(4);
                for(int i = 0; i < eligibleArray.size(); i++) {
                    eligibleSet.add(eligibleArray.get(i).asText());
                }
                options.setExcluded(excludedSet);
                options.setEligible(eligibleSet);
            }
        }
        
        app.publishEvent(clientSocket.getSessionId(), topic, event, options);
        
    }

    
    public void onEvent(String publisherId, WampTopic topic, JsonNode event, WampPublishOptions pubOptions) throws Exception { 
        String msgByVersion[] = new String[WampApplication.WAMPv2+1];  // Cache EVENT message for each WAMP version

        if(pubOptions == null) pubOptions = new WampPublishOptions(null);

        Set<String> eligible = pubOptions.getEligible();
        if(eligible == null) eligible = topic.getSessionIds();
        else eligible.retainAll(topic.getSessionIds());
        
        Set<String> excluded = pubOptions.getExcluded();
        if(excluded == null) excluded = new HashSet<String>();        
        if(pubOptions.hasExcludeMe()) excluded.add(publisherId);
        
        for (String sid : eligible) {
            if((excluded==null) || (!excluded.contains(sid))) {
                WampSubscription subscription = topic.getSubscription(sid);
                WampSubscriptionOptions subOptions = subscription.getOptions();
                if(subOptions != null && subOptions.hasEventsEnabled()) {
                    WampSocket socket = subscription.getSocket();
                    synchronized(socket) {
                        if(socket != null && socket.isOpen() && !excluded.contains(sid)) {
                            if(socket.supportVersion(WampApplication.WAMPv2)) {
                                if(msgByVersion[WampApplication.WAMPv2] == null) {
                                    String eventDetails = (!pubOptions.hasIdentifyMe())? "" : ", { \"PUBLISHER\": \"" + publisherId + "\" }";
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
    
    
    public void onMetaEvent(WampTopic topic, String metatopic, JsonNode metaevent, WampSocket toClient) throws Exception 
    { 
        String msg = "[129,\"" + topic.getURI() + "\", \"" + metatopic + "\"";
        if(metaevent != null) msg += ", " + metaevent.toString();
        msg += "]";
        
        if(toClient != null) {
            toClient.sendSafe(msg);
        } else {
            for(String sid : topic.getSessionIds()) {
                WampSubscription subscriber = topic.getSubscription(sid);
                if(subscriber.getOptions() != null && subscriber.getOptions().hasMetaEventsEnabled()) {
                    WampSocket remoteSocket = subscriber.getSocket();
                    remoteSocket.sendSafe(msg);
                }
            }
        }
    }
    
}
