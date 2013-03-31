package com.github.jmarine.wampservices;

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
    public Object onCall(WampSocket clientSocket, String methodName, ArrayNode args) throws Exception 
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
            if(options != null && options.isMetaEventsEnabled()) {
                app.publishMetaEvent(topic, WampMetaTopic.OK, null, clientSocket);
                
                if(options.isEventsEnabled()) {
                    app.publishMetaEvent(topic, WampMetaTopic.JOINED, subscription.toJSON(), null);
                }
            }
        }         
    }

    public void onUnsubscribe(WampSocket clientSocket, WampTopic topic) throws Exception { 
        WampSubscription subscription = topic.getSubscription(clientSocket.getSessionId());
        if(subscription.refCount(-1) <= 0) {
            WampSubscriptionOptions options = subscription.getOptions();
            if(options!=null && options.isMetaEventsEnabled() && options.isEventsEnabled()) {
                ObjectNode metaevent = subscription.toJSON();
                app.publishMetaEvent(topic, WampMetaTopic.LEFT, metaevent, null);
            }
            topic.removeSubscription(subscription.getSocket().getSessionId());
            clientSocket.removeSubscription(subscription.getTopicUriOrPattern());
        }
    }
    
    public void onPublish(WampSocket clientSocket, WampTopic topic, ArrayNode request) throws Exception 
    {
        JsonNode event = request.get(2);
        if(request.size() == 3) {
            clientSocket.publishEvent(topic, event, false);
        } else if(request.size() == 4) {
            // Argument 4 could be a BOOLEAN(excludeMe) or JSONArray(excludedIds)
            try {
                boolean excludeMe = request.get(3).asBoolean();
                clientSocket.publishEvent(topic, event, excludeMe);
            } catch(Exception ex) {
                HashSet<String> excludedSet = new HashSet<String>();
                ArrayNode excludedArray = (ArrayNode)request.get(3);
                for(int i = 0; i < excludedArray.size(); i++) {
                    excludedSet.add(excludedArray.get(i).asText());
                }
                app.publishEvent(clientSocket.getSessionId(), topic, event, excludedSet, null);
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
            app.publishEvent(clientSocket.getSessionId(), topic, event, excludedSet, eligibleSet);
        }
        
    }

    
    public void onEvent(String publisherId, WampTopic topic, JsonNode event, Set<String> excluded, Set<String> eligible) throws Exception { 
        String msgV1 = null;  // Cache EVENT message for WAMP v1
        String msgV2 = null;  // Cache EVENT message for WAMP v2
        for (String sid : eligible) {
            if((excluded==null) || (!excluded.contains(sid))) {
                WampSubscription subscription = topic.getSubscription(sid);
                WampSubscriptionOptions options = subscription.getOptions();
                if(options != null && options.isEventsEnabled()) {
                    WampSocket socket = subscription.getSocket();
                    synchronized(socket) {
                        if(socket != null && socket.isOpen() && !excluded.contains(sid)) {
                            if( (topic.getOptions().isPublisherIdRevelationEnabled())
                                    && (subscription.getOptions().isPublisherIdRequested())) {
                                if(msgV2 == null) msgV2 = "[8,\"" + topic.getURI() + "\", " + event.toString() + ",\"" + publisherId +"\"]";
                                socket.sendSafe(msgV2);
                            } else {
                                if(msgV1 == null) msgV1 = "[8,\"" + topic.getURI() + "\", " + event.toString() + "]";
                                socket.sendSafe(msgV1);
                            }
                        }
                    }
                }
            }
        }      
    }
    
    
    public void onMetaEvent(WampTopic topic, String metatopic, JsonNode metaevent, WampSocket toClient) throws Exception 
    { 
        String msg = "[9,\"" + topic.getURI() + "\", \"" + metatopic + "\"";
        if(metaevent != null) msg += ", " + metaevent.toString();
        msg += "]";
        
        if(toClient != null) {
            toClient.sendSafe(msg);
        } else {
            for(String sid : topic.getSessionIds()) {
                WampSubscription subscriber = topic.getSubscription(sid);
                if(subscriber.getOptions() != null && subscriber.getOptions().isMetaEventsEnabled()) {
                    WampSocket remoteSocket = subscriber.getSocket();
                    remoteSocket.sendSafe(msg);
                }
            }
        }
    }
    
}
