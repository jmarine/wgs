/**
 * WebSocket Message Application Protocol implementation
 *
 * @author Jordi Marine Fort 
 */

package com.github.jmarine.wampservices;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfiguration;


public class WampEndpoint extends Endpoint 
{
    private static final Logger logger = Logger.getLogger(WampEndpoint.class.getName());

    private Session session;
    private WampApplication application;
    
   
    public WampEndpoint() { 
        logger.fine("##################### WAMP ENDPOINT CREATED");
    }
    


    public Session getSession()
    {
        return session;
    }
    
    
    public WampApplication getWampApplication() 
    {
        return application;
    }
    
    
    public void onApplicationStart(WampApplication app) { }
   
    @Override
    public void onOpen(Session session, EndpointConfiguration endpointConfiguration) {
        logger.fine("##################### Session opened");
        
        String path = ((ServerEndpointConfiguration)endpointConfiguration).getPath();
        application = (WampApplication)endpointConfiguration;
        if(application.start()) onApplicationStart(application);
        
        this.session = session;
        
        application.onWampOpen(session);
    }
    
   
    @Override
    public void onClose(Session session, CloseReason reason) 
    {
        super.onClose(session, reason);
        application.onWampClose(session, reason);
        logger.fine("##################### Session closed: " + session);
    }
    
    
    @Override
    public void onError(Session session, Throwable thr) 
    {
         super.onError(session, thr);        
         logger.fine("##################### Session error");
         onClose(session, new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "onError"));
    }    

}
