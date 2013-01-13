package com.github.jmarine.wampservices.samples;


import com.github.jmarine.wampservices.WampApplication;
import com.github.jmarine.wampservices.WampEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Session;
import javax.websocket.WebSocketClose;
import javax.websocket.WebSocketError;
import javax.websocket.WebSocketOpen;
import javax.websocket.server.WebSocketEndpoint;


@WebSocketEndpoint(value="/wgs", configuration=WampApplication.class, subprotocols={"wamp"})
public class WgsEndpoint extends WampEndpoint
{
    @Override
    public void onApplicationStart(WampApplication app) { 
        super.onApplicationStart(app);
        app.registerWampModule(com.github.jmarine.wampservices.wgs.Module.class); 
    }
    
    @WebSocketOpen
    public void wsOpened(Session session, EndpointConfiguration endpointConfiguration) {
        super.onOpen(session, endpointConfiguration);
    }
    
    @WebSocketClose
    public void wsClosed(Session session) {
        super.onClose(session, null);
    }

    @WebSocketError
    public void wsError(Session session, Throwable thr) {
        super.onError(session, thr);
    }

}
