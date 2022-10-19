package org.wgs.wamp.transport.http.longpolling;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.websocket.CloseReason;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampSocket;


public class WampLongPollingSocket extends WampSocket
{
    private LinkedBlockingQueue<Object> queue;
    private HttpSession session;
    private String negotiatedSubprotocol;
    
    
    public WampLongPollingSocket(WampApplication app, HttpServletRequest request, LinkedBlockingQueue<Object> queue) 
    {
        this.queue = queue;
        this.session = request.getSession();
        setUserPrincipal(request.getUserPrincipal());

        List<String> supportedSubprotocols = getSupportedSubprotocols();
        this.negotiatedSubprotocol = supportedSubprotocols.get(0);  // "wamp.2.json" by default
        try(JsonReader jsonReader = Json.createReader(request.getReader())) {
            JsonObject negotiationInfo = jsonReader.readObject();
            JsonArray  subprotocols = negotiationInfo.getJsonArray("protocols");
            if(subprotocols != null && subprotocols.size() > 0) {
                for(int i = 0; i < subprotocols.size(); i++) {
                    String subprotocol = subprotocols.getString(i);
                    if(supportedSubprotocols.contains(subprotocol)) {
                        this.negotiatedSubprotocol = subprotocol;
                        break;
                    }
                }
            }
        } catch(Exception ex) { 
            System.err.println("Error: " + ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace();
        } 
    }

    
    private HttpSession getSession()
    {
        return session;
    }
    
    @Override
    public Object getSessionData(String key) 
    {
        return getSession().getAttribute(key);
    }

    @Override
    public void putSessionData(String key, Object val) 
    {
        getSession().setAttribute(key, val);
    }    
    
    @Override
    public Object removeSessionData(String key) 
    {
        Object old = getSessionData(key);
        getSession().removeAttribute(key);
        return old;
    }    
    
    @Override
    public boolean containsSessionData(String key) 
    {
        return (getSessionData(key) != null);
    }  
    

    @Override
    public String getNegotiatedSubprotocol()
    {
        return negotiatedSubprotocol;
    }
    
    private List<String> getSupportedSubprotocols()
    {
        List<String> subprotocols = java.util.Arrays.asList("wamp.2.json", "wamp.2.msgpack", "wamp.2.json.batched", "wamp.2.msgpack.batched");
        return subprotocols;
    }
    
    
    @Override
    public void sendObject(Object msg) throws Exception
    {
        queue.add(msg);
    }
    
    
    @Override
    public boolean close(CloseReason reason)
    {
        System.out.println("WampLongPollingServlet: close");
        if(super.close(reason)) {
            // The previous GOODBYE message will completes "/receive" AsyncContext
            return true;
        }
        return false;
    }
    
    
}
