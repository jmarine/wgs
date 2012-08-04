package com.github.jmarine.wampservices;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;


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

    public Object onCall(WampSocket clientSocket, String methodName, JSONArray args) throws Exception 
    {
        Method method = rpcs.get(methodName);
        if(method != null) {
            int argCount = 0;
            ArrayList params = new ArrayList();
            for(Class paramType : method.getParameterTypes()) {
                if(paramType.isInstance(clientSocket)) {
                    params.add(clientSocket);
                } else if(JSONArray.class.isAssignableFrom(paramType)) {
                    params.add(args);  // TODO: only from argCount to args.length()
                    argCount = args.length();
                } else if(JSONObject.class.isAssignableFrom(paramType)) {
                    params.add(args.getJSONObject(argCount++));
                } else if(String.class.isAssignableFrom(paramType)) {
                    params.add(args.getString(argCount++));
                } else if(Boolean.class.isAssignableFrom(paramType)) {
                    params.add(args.getBoolean(argCount++));
                } else if(Integer.class.isAssignableFrom(paramType)) {
                    params.add(args.getInt(argCount++));
                } else if(Double.class.isAssignableFrom(paramType)) {
                    params.add(args.getDouble(argCount++));
                }
            }
            return method.invoke(this, params.toArray());
        }

        throw new WampException(WampException.WAMP_GENERIC_ERROR_URI, "Method not implemented: " + methodName);
    }
    
    public void   onSubscribe(WampSocket clientSocket, WampTopic topic) throws Exception { 
        clientSocket.addTopic(topic);
        topic.addSocket(clientSocket);
    }

    public void   onUnsubscribe(WampSocket clientSocket, WampTopic topic) throws Exception { 
        clientSocket.removeTopic(topic);
        topic.removeSocket(clientSocket);
    }
    
    public void   onPublish(WampSocket clientSocket, WampTopic topic, JSONObject event, Set<String> excluded, Set<String> eligible) throws Exception { 
        String msg = "[8,\"" + topic.getURI() + "\", " + event.toString() + "]";
        for (String cid : eligible) {
            if((excluded==null) || (!excluded.contains(cid))) {
                WampSocket socket = topic.getSocket(cid);
                if(socket != null && socket.isConnected() && !excluded.contains(cid)) {
                    socket.sendSafe(msg);
                }
            }
        }      
    }
    
}
