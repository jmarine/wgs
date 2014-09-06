package org.wgs.wamp.transport.http.longpolling;

import java.util.concurrent.LinkedBlockingQueue;
import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampSocket;


public class WampLongPollingSocket extends WampSocket
{
    private LinkedBlockingQueue<Object> queue;
    private HttpSession  session;
    
    
    public WampLongPollingSocket(WampApplication app, HttpServletRequest request, LinkedBlockingQueue<Object> queue) 
    {
        super(app);
        this.queue = queue;
        this.session = request.getSession();
        setUserPrincipal(request.getUserPrincipal());
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
        System.out.println("WampLongPollingServlet: close");
        if(super.close(reason)) {
            // The previous GOODBYE message will completes "/receive" AsyncContext
            return true;
        }
        return false;
    }
    
    
}
