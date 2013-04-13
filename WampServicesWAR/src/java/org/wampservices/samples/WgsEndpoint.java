package org.wampservices.samples;


import org.wampservices.WampApplication;
import org.wampservices.WampEndpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.server.ServerEndpoint;


@ServerEndpoint(value="/wgs", configurator=WampApplication.class, subprotocols={"wamp"})
public class WgsEndpoint extends WampEndpoint
{
    @Override
    public void onApplicationStart(WampApplication app) { 
        super.onApplicationStart(app);
        app.registerWampModule(org.wampservices.wgs.Module.class); 
    }
    
    @OnOpen
    public void wsOpened(Session session, EndpointConfig endpointConfiguration) {
        super.onOpen(session, endpointConfiguration);
    }
    
    @OnClose
    public void wsClosed(Session session) {
        super.onClose(session, null);
    }

    @OnError
    public void wsError(Session session, Throwable thr) {
        super.onError(session, thr);
    }

}
