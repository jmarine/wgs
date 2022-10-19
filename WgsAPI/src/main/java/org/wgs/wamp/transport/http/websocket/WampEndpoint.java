package org.wgs.wamp.transport.http.websocket;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;

import org.wgs.security.WampCRA;
import org.wgs.wamp.WampApplication;


public class WampEndpoint extends Endpoint 
{
    private static final Logger logger = Logger.getLogger(WampEndpoint.class.getName());

    private Session session;
    private WampEndpointConfig wampEndpointConfig;
    
   
    public WampEndpoint() { 
    }
    

    
    public void onApplicationStart(WampApplication app) { 
    }
   
    @OnOpen
    @Override
    public  void onOpen(Session session, EndpointConfig endpointConfig) {
        if(logger.isLoggable(Level.FINEST)) logger.log(Level.FINEST, "Session opened ("+ session.getNegotiatedSubprotocol() +"): " + session.getId());
        
        Map<String,Object> configProps = endpointConfig.getUserProperties();
        session.getUserProperties().put(WampCRA.WAMP_AUTH_ID_PROPERTY_NAME, configProps.get(WampCRA.WAMP_AUTH_ID_PROPERTY_NAME) );
        
        this.session = session;
        try {        
            if(endpointConfig != null && endpointConfig instanceof WampEndpointConfig) {
                this.wampEndpointConfig = (WampEndpointConfig)endpointConfig;
            } else {
                this.wampEndpointConfig = (WampEndpointConfig)configProps.get(WampEndpointConfig.WAMP_ENDPOINTCONFIG_PROPERTY_NAME);
            }
            this.wampEndpointConfig.onWampOpen(session, this);
        } catch(Throwable ex) {
            System.out.println("Error: " + ex.getMessage());
            ex.printStackTrace();
            throw ex;
        }
    }

    
    @Override
    public void onClose(Session session, CloseReason reason) 
    {
        logger.log(Level.FINEST, "Session closed: " + session);
        super.onClose(session, reason);
        wampEndpointConfig.onWampClose(session, reason);
    }
    
    
    @Override
    public void onError(Session session, Throwable thr) 
    {
         logger.log(Level.FINEST, "Session error");
         super.onError(session, thr);        
         onClose(session, new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "wamp.close.error"));
    }    

}
