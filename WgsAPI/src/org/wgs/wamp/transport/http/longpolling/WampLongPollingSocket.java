package org.wgs.wamp.transport.http.longpolling;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampSocket;


public class WampLongPollingSocket extends WampSocket
{
    private LinkedBlockingQueue<Object> queue;
    private HttpSession session;
    private String negotiatedSubprotocol;
    
    
    public WampLongPollingSocket(WampApplication app, HttpServletRequest request, LinkedBlockingQueue<Object> queue) 
    {
        super(app);
        this.queue = queue;
        this.session = request.getSession();
        setUserPrincipal(request.getUserPrincipal());

        List<String> supportedSubprotocols = getSupportedSubprotocols();
        this.negotiatedSubprotocol = supportedSubprotocols.get(0);  // "wamp.2.json" by default
        try(JsonReader jsonReader = Json.createReader(request.getInputStream())) {
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
    public void sendObject(Object msg) 
    {
        try {
            if(isOpen()) {
                queue.add(msg);
            }

        } catch(Exception e) {
            //close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "wamp.close.error"));
        }
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
