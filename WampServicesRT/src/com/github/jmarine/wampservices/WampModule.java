package com.github.jmarine.wampservices;

import org.json.JSONArray;
import org.json.JSONObject;


public abstract class WampModule 
{
    private WampApplication app;
    
    public WampModule(WampApplication app) {
        this.app = app;
    }
    
    public WampApplication getWampApplication()
    {
        return app;
    }
    
    public abstract String  getBaseURL();
    
    public void   onConnect(WampSocket clientSocket) throws Exception { }
    
    public void   onDisconnect(WampSocket clientSocket) throws Exception { }

    public Object onCall(WampSocket clientSocket, String method, JSONArray args) throws Exception {
        throw new WampException(WampException.WAMP_GENERIC_ERROR_URI, "Method not implemented: " + method);
    }
    
    public void   onSubscribe(WampSocket clientSocket, WampTopic topic) throws Exception { }

    public void   onUnsubscribe(WampSocket clientSocket, WampTopic topic) throws Exception { }
    
    public void   onPublish(WampSocket clientSocket, WampTopic topic, JSONObject event) throws Exception { }
    
}
