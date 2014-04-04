/**
 * WebSocket Message Application Protocol implementation
 *
 * @author Jordi Marine Fort 
 */

package org.wgs.wamp.transport.http.websocket;

import org.wgs.wamp.WampApplication;
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
    private WampEndpointConfig wampEndpointConfig;
    
   
    public WampEndpoint() { 
        logger.fine("##################### WAMP ENDPOINT CREATED");
    }
    


    public Session getSession()
    {
        return session;
    }
    
    
    
    public void onApplicationStart(WampApplication app) { }
   
    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        logger.fine("##################### Session opened");

        this.session = session;
        this.wampEndpointConfig = (WampEndpointConfig)endpointConfig.getUserProperties().get(WampEndpointConfig.WAMP_ENDPOINTCONFIG_PROPERTY_NAME);
        
        wampEndpointConfig.onWampOpen(session, this);
    }
    
    
   
    @Override
    public void onClose(Session session, CloseReason reason) 
    {
        super.onClose(session, reason);
        wampEndpointConfig.onWampClose(session, reason);
        logger.fine("##################### Session closed: " + session);
    }
    
    
    @Override
    public void onError(Session session, Throwable thr) 
    {
         super.onError(session, thr);        
         logger.fine("##################### Session error");
         onClose(session, new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "wamp.close.error"));
    }    

}
