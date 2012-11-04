package com.github.jmarine.wampservices.samples;

import com.github.jmarine.wampservices.WampApplication;
import javax.net.websocket.CloseReason;
import javax.net.websocket.Session;
import javax.net.websocket.annotations.WebSocketClose;
import javax.net.websocket.annotations.WebSocketEndpoint;
import javax.net.websocket.annotations.WebSocketError;
import javax.net.websocket.annotations.WebSocketOpen;


@WebSocketEndpoint(value="/wgs")
public class WgsEndpoint extends WampApplication 
{
    public WgsEndpoint()
    {
        try { 
            this.registerWampModule(com.github.jmarine.wampservices.wgs.Module.class); 
        } catch(Exception ex) {
            System.err.println("WgsEndpoing: Error registering WGS module");
            ex.printStackTrace();
        }
    }
    
    @WebSocketOpen
    public void wsOpened(Session session) {
        super.onOpen(session);
    }
    
    @WebSocketClose
    public void wsClosed(Session session) {
        super.onClose(session, null);
    }

    @WebSocketError
    public void wsError(Throwable thr, Session session) {
        super.onError(thr, session);
    }
    

}
