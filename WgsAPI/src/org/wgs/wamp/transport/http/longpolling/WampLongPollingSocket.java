package org.wgs.wamp.transport.http.longpolling;

import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.type.WampConnectionState;
import org.wgs.wamp.type.WampObject;


public class WampLongPollingSocket extends WampSocket
{
    private String wampSessionId;
    private AsyncContext asyncContext;
    private WampLongPollingServlet servlet;
    private LinkedBlockingQueue<Object> queue;
    
    
    public WampLongPollingSocket(WampApplication app, WampLongPollingServlet servlet, String wampSessionId, LinkedBlockingQueue<Object> queue) 
    {
        super(app);
        this.servlet = servlet;
        this.wampSessionId = wampSessionId;
        this.queue = queue;
        
        AsyncContext asyncContext = servlet.getAsyncContext(wampSessionId);
        HttpServletRequest request = (HttpServletRequest)asyncContext.getRequest();
        setUserPrincipal(request.getUserPrincipal());
    }
    
    
    
    private HttpSession getSession()
    {
        AsyncContext asyncContext = servlet.getAsyncContext(wampSessionId);        
        HttpServletRequest request = (HttpServletRequest)asyncContext.getRequest();
        return request.getSession();
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
        return "wamp.2.json";
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
        System.out.println("WampLPSocket: close");
        if(super.close(reason)) {
            // The previous GOODBYE message will completes "/receive" AsyncContext
            return true;
        }
        return false;
    }
    
    
}
