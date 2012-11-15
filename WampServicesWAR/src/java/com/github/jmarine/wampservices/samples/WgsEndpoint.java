package com.github.jmarine.wampservices.samples;


import com.github.jmarine.wampservices.WampApplication;
import com.github.jmarine.wampservices.WampEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.Session;
import javax.websocket.WebSocketClose;
import javax.websocket.WebSocketEndpoint;
import javax.websocket.WebSocketError;
import javax.websocket.WebSocketOpen;


@WebSocketEndpoint(value="/wgs")
public class WgsEndpoint extends WampEndpoint
{
    private static WampApplication wgsApplication = new WampApplication() {
            {   // annonymous class constructor:    
                registerWampModule(com.github.jmarine.wampservices.wgs.Module.class); 
            } 
        };
    
    public WgsEndpoint() {
        super(wgsApplication);
    }
    
    @WebSocketOpen
    public void wsOpened(Session session) {
        super.onOpen(session);
    }
    
    @WebSocketClose
    public void wsClosed() {
        super.onClose(null);
    }

    @WebSocketError
    public void wsError(Throwable thr) {
        super.onError(thr);
    }

}
