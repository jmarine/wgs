package com.github.jmarine.wampservices;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
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
    
    public abstract String  getBaseURL();
    
    public void   onConnect(WampSocket clientSocket) throws Exception { }
    
    public void   onDisconnect(WampSocket clientSocket) throws Exception { }

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
                } else if(ObjectNode.class.isAssignableFrom(paramType)) {
                    params.add(args.get(argCount++));
                } else {
                    params.add(mapper.readValue(args.get(argCount++), paramType));
                }
            }
            return method.invoke(this, params.toArray());
        }

        throw new WampException(WampException.WAMP_GENERIC_ERROR_URI, "Method not implemented: " + methodName);
    }
    
    public void   onSubscribe(WampSocket clientSocket, WampTopic topic, WampSubscription subscription) throws Exception { 
        topic.addSubscription(subscription);
        clientSocket.addSubscription(subscription);
    }

    public void   onUnsubscribe(WampSocket clientSocket, WampTopic topic, WampSubscription subscription) throws Exception { 
        topic.removeSubscription(subscription);
        clientSocket.removeSubscription(subscription.getTopicUriOrPattern());
    }
    
    public void   onPublish(WampSocket clientSocket, WampTopic topic, ArrayNode request) throws Exception 
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
                clientSocket.publishEvent(topic, event, excludedSet, null);
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
            clientSocket.publishEvent(topic, event, excludedSet, eligibleSet);
        }
        
    }
    
    public void   onEvent(WampSocket clientSocket, WampTopic topic, JsonNode event, Set<String> excluded, Set<String> eligible) throws Exception { 
        String msg = "[8,\"" + topic.getURI() + "\", " + event.toString() + "]";
        for (String sid : eligible) {
            if((excluded==null) || (!excluded.contains(sid))) {
                WampSocket socket = topic.getSubscription(sid).getSocket();
                if(socket != null && socket.isConnected() && !excluded.contains(sid)) {
                    socket.sendSafe(msg);
                }
            }
        }      
    }
    
}
