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
import javax.websocket.DefaultServerConfiguration;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Session;


public class WampEndpoint extends Endpoint 
{
    private static final Logger logger = Logger.getLogger(WampEndpoint.class.getName());

    private Session session;
    private WampApplication application;
    
   
    public WampEndpoint() { 
        logger.info("WAMP ENDPOINT CREATED");
    }
    
    public WampEndpoint(WampApplication application)
    {
        logger.info("WAMP ENDPOINT CREATED");
        this.application = application;
    }
    

    public Session getSession()
    {
        return session;
    }
    
    
    public WampApplication getWampContext() 
    {
        return application;
    }
    
    
   
    @Override
    public EndpointConfiguration getEndpointConfiguration() {
        DefaultServerConfiguration config = null;
        config = new DefaultServerConfiguration("") {
            @Override
            public boolean matchesURI(URI uri) {
                return true;
            }
            
            @Override
            public String getNegotiatedSubprotocol(List<String> requestedSubprotocols) {
                return "wamp";
            }
            
            @Override
            public List<String> getNegotiatedExtensions(List<String> requestedExtensions) {
                String supportedExtensions[] = {};
                List<String> result = new ArrayList<String>();
                for (String requestedExtension : requestedExtensions) {
                    for (String extension : supportedExtensions) {
                        if(extension.equals(requestedExtension)){
                            result.add(requestedExtension);
                        }
                    }
                }                
                return result;
            }
            
            @Override
            public boolean checkOrigin(String originHeaderValue) {
                return true;
            }
            
        };
        return config;
    }
    

    @Override
    public void onOpen(Session session) {
        System.out.println("##################### Session opened");
        
        this.session = session;
        if(application == null) {
            String path = session.getRequestURI().getPath();
            application = WampApplication.getApplication(path);
        }
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
