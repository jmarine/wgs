/**
 * WebSocket Message Application Protocol implementation
 *
 * @author Jordi Marine Fort 
 */

package org.wgs.wamp.transport.http.websocket;

import java.util.Map;
import java.util.logging.Logger;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import org.wgs.security.WampCRA;
import org.wgs.wamp.WampApplication;


public class WampEndpoint extends Endpoint 
{
    private static final Logger logger = Logger.getLogger(WampEndpoint.class.getName());

    private Session session;
    private WampEndpointConfig wampEndpointConfig;
    
   
    public WampEndpoint() { 
        logger.fine("##################### WAMP ENDPOINT CREATED");
    }
    

    
    public void onApplicationStart(WampApplication app) { }
   
    @Override
    public  void onOpen(Session session, EndpointConfig endpointConfig) {
        logger.fine("##################### Session opened");
        
        Map<String,Object> configProps = endpointConfig.getUserProperties();
        session.getUserProperties().put(WampCRA.WAMP_AUTH_ID_PROPERTY_NAME,
            configProps.get(WampCRA.WAMP_AUTH_ID_PROPERTY_NAME) );
        
        this.session = session;
        this.wampEndpointConfig = (WampEndpointConfig)configProps.get(WampEndpointConfig.WAMP_ENDPOINTCONFIG_PROPERTY_NAME);
        this.wampEndpointConfig.onWampOpen(session, this);
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
