package org.wgs.wamp.transport.http.websocket;

import java.nio.ByteBuffer;
import org.wgs.wamp.*;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.types.WampObject;
import org.wgs.wamp.types.WampList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampSocket;


public class WampEndpointConfig 
    extends javax.websocket.server.ServerEndpointConfig.Configurator
    implements javax.websocket.server.ServerEndpointConfig
{
    private static final Logger logger = Logger.getLogger(WampEndpointConfig.class.getName());

    private Class endpointClass;
    private WampApplication application;
    private ConcurrentHashMap<String,WampSocket> sockets;    
    

    public WampEndpointConfig(Class endpointClass, WampApplication application)
    {
        this.application = application;
        this.endpointClass = endpointClass;
        this.sockets = new ConcurrentHashMap<String,WampSocket>();        
    }
    
    
    public WampApplication getWampApplication()
    {
        return this.application;
    }
    
    public WampSocket getWampSocket(Session session) 
    {
        WampSocket clientSocket = sockets.get(session.getId());
        if(clientSocket == null) {
            clientSocket = new WampSocket(application, session);
            sockets.put(session.getId(), clientSocket);
        }
        return clientSocket;
    }

    
    public void onWampOpen(final Session session, WampEndpoint endpoint) {
        System.out.println("##################### Session opened");

        final WampSocket clientSocket = getWampSocket(session);
        
        if(application.start()) endpoint.onApplicationStart(application);
        application.onWampOpen(clientSocket);



        session.setMaxIdleTimeout(0L);  // forever
        
        String subproto = (session.getNegotiatedSubprotocol());
        
        if(subproto != null && subproto.equalsIgnoreCase("wamp.2.msgpack")) {
            session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                @Override
                public void onMessage(byte[] message) {
                    try {
                        System.out.println("onWampMessage (binary): " + message);
                        WampList request = (WampList)WampObject.getSerializer(WampEncoding.MsgPack).deserialize(message);
                        WampEndpointConfig.this.application.onWampMessage(clientSocket, request);
                    } catch(Exception ex) { 
                        logger.log(Level.SEVERE, "Error processing message: "+message, ex);
                    }
                }

            });
            
        } else {
            
            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String message) {
                    try {
                        System.out.println("onWampMessage (text): " + message);
                        WampList request = (WampList)WampObject.getSerializer(WampEncoding.JSon).deserialize(message);
                        WampEndpointConfig.this.application.onWampMessage(clientSocket, request);
                    } catch(Exception ex) { 
                        logger.log(Level.SEVERE, "Error processing message: "+message, ex);
                    }
                }

            });

        }
        
        // Send WELCOME message to client:
        WampProtocol.sendWelcomeMessage(application, clientSocket);
       
    }   


    
    public void onWampClose(Session session, CloseReason reason) 
    {
        WampSocket clientSocket = sockets.remove(session.getId());
        if(clientSocket != null) {
            application.onWampClose(clientSocket, reason);
            logger.log(Level.INFO, "Socket disconnected: {0}", new Object[] {clientSocket.getSessionId()});
        }
        
    }
    

    
    @Override
    public Class<?> getEndpointClass() {
        return endpointClass;
    }

    @Override
    public String getPath() {
        return application.getPath();
    }

    @Override
    public List<String> getSubprotocols() {
        List<String> subprotocols = java.util.Arrays.asList("wamp.2.json");  // , "wamp.2.msgpack");
        return subprotocols;
    }

    @Override
    public List<Extension> getExtensions() {
        List<Extension> extensions = Collections.emptyList();
        return extensions;
    }

    @Override
    public List<Class<? extends Encoder>> getEncoders() {
        List<Class<? extends Encoder>> encoders = Collections.emptyList();
        return encoders;
    }

    @Override
    public List<Class<? extends Decoder>> getDecoders() {
        List<Class<? extends Decoder>> decoders = Collections.emptyList();
        return decoders;
    }

    @Override
    public Map<String, Object> getUserProperties() {
        Map<String, Object> userProperties = new HashMap<String, Object>();
        return userProperties;
    }
    
    @Override
    public Configurator getConfigurator() {
        return this;
    }    

    @Override
    public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
        String subprotocol = "wamp.2.json";
        if (requested != null) {
            for (String clientProtocol : requested) {
                if (supported.contains(clientProtocol)) {
                    subprotocol = clientProtocol;
                    break;
                }
            }
        }
        return subprotocol;
    }
    
}
