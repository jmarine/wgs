/**
 * WebSocket Message Application Protocol implementation
 *
 * @author Jordi Marine Fort 
 */

package com.github.jmarine.wampservices;

import java.util.logging.Logger;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Session;


public class WampEndpoint extends Endpoint 
{
    private static final Logger logger = Logger.getLogger(WampEndpoint.class.getName());

   
    private Session session;
    private WampApplication application;
   
    
    public WampEndpoint(WampApplication application) 
    {
        setWampApplication(application);
    }
    
    public void setWampApplication(WampApplication application) 
    {
        this.application = application;
    }


    public WampApplication getWampApplication() 
    {
        return application;
    }
    
    public Session getSession()
    {
        return session;
    }
    
    
   
    @Override
    public EndpointConfiguration getEndpointConfiguration() {
        return null;
    }
    

    @Override
    public void onOpen(Session session) {
        System.out.println("##################### Session opened");
        
        this.session = session;
        application.onWampOpen(session);
    }
    
   
    @Override
    public void onClose(CloseReason reason) 
    {
        super.onClose(reason);
        application.onWampClose(session, reason);
    }
    
    
    @Override
    public void onError(Throwable thr) 
    {
         super.onError(thr);        
         System.out.println("##################### Session error");
         onClose(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "onError"));
    }    

}
