/**
 * WebSocket Message Application Protocol implementation
 *
 * @author Jordi Marine Fort 
 */

package org.wgs.wamp;

import java.util.logging.Logger;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfig;


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
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        logger.fine("##################### Session opened");
        
        String path = ((ServerEndpointConfig)endpointConfig).getPath();
        application = (WampApplication)endpointConfig;
        if(application.start()) onApplicationStart(application);
        
        this.session = session;
        
        application.onWampOpen(session, endpointConfig);
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
