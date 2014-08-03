package org.wgs.wamp.transport.http.websocket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.CloseReason;
import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import org.wgs.security.WampCRA;
import org.wgs.wamp.*;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.encoding.WampSerializerBatchedJSON;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampObject;


public class WampEndpointConfig 
    extends javax.websocket.server.ServerEndpointConfig.Configurator
    implements javax.websocket.server.ServerEndpointConfig
{
    public  static final String WAMP_APPLICATION_PROPERTY_NAME = "__wamp_application";
    public  static final String WAMP_ENDPOINTCONFIG_PROPERTY_NAME = "__wamp_endpointconfig";
    public  static final String WAMP_AUTH_COOKIE_NAME = "__wamp_authcookie";

    private static final Logger logger = Logger.getLogger(WampEndpointConfig.class.getName());
    
    private Class endpointClass;
    private WampApplication application;
    private Map<String, Object> userProperties;
    
    static private ConcurrentHashMap<String,WampSocket> sockets = new ConcurrentHashMap<String,WampSocket>();
    

    public WampEndpointConfig(Class endpointClass, WampApplication application)
    {
        this.application = application;
        this.endpointClass = endpointClass;
        this.userProperties = new HashMap<String,Object>();
    }
    
    
    public WampApplication getWampApplication()
    {
        return this.application;
    }
    
    
    public static WampSocket getWampSocket(WampApplication application, Session session) 
    {
        WampSocket clientSocket = sockets.get(session.getId());
        if(clientSocket == null) {
            clientSocket = new WampSocket(application, session);
            sockets.put(session.getId(), clientSocket);
        }
        return clientSocket;
    }

    
    public void onWampOpen(final Session session, WampEndpoint endpoint) {
        WampSocket clientSocket = getWampSocket(application, session);
        session.setMaxIdleTimeout(0L);  // forever (but fails on Jetty)

        addWampMessageHandlers(application, session);
        
        if(application.start()) endpoint.onApplicationStart(application);
        application.onWampOpen(clientSocket);
       
    }   
    
    public static void addWampMessageHandlers(final WampApplication wampApp, final Session session)
    {
        final WampSocket clientSocket = getWampSocket(wampApp, session);
        
        String subproto = (session.getNegotiatedSubprotocol());
        if(logger.isLoggable(Level.FINE)) logger.log(Level.FINE, "Negotiated subprotocol: " + subproto);
        
        if(subproto != null && subproto.equalsIgnoreCase("wamp.2.msgpack")) {
            session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                @Override
                public void onMessage(byte[] message) {
                    try {
                        if(logger.isLoggable(Level.FINEST)) logger.log(Level.FINEST, "onWampMessage (binary msgpack)");
                        WampList request = (WampList)WampEncoding.MsgPack.getSerializer().deserialize(message, 0, message.length);
                        if(logger.isLoggable(Level.FINEST)) logger.log(Level.FINEST, "onWampMessage (deserialized request): " + request);
                        wampApp.onWampMessage(clientSocket, request);
                    } catch(Exception ex) { 
                        logger.log(Level.SEVERE, "Error processing message: "+message, ex);
                    }
                }

            });
            
        } else if(subproto != null && subproto.equalsIgnoreCase("wamp.2.msgpack.batched")) {
            session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                @Override
                public void onMessage(byte[] message) {
                    try {
                        int offset = 0;
                        while(offset < message.length) {
                            int partLen = java.nio.ByteBuffer.wrap(message, offset, 4).getInt();                            
                            if(logger.isLoggable(Level.FINEST)) logger.log(Level.FINEST, "onWampMessage (partial msgpack batched)");
                            WampList request = (WampList)WampEncoding.MsgPack.getSerializer().deserialize(message, offset+4, partLen);
                            if(logger.isLoggable(Level.FINEST)) logger.log(Level.FINEST, "onWampMessage (deserialized request): " + request);
                            wampApp.onWampMessage(clientSocket, request);
                            offset = offset + 4 + partLen;
                        }
                    } catch(Exception ex) { 
                        logger.log(Level.SEVERE, "Error processing message: "+message, ex);
                    }
                }

            });            
            
        } else if(subproto != null && subproto.equalsIgnoreCase("wamp.2.json.batched")) {            
            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String message) {
                    try {
                        int offset = 0;
                        while(offset < message.length()) {
                            int partLen = message.indexOf(WampSerializerBatchedJSON.MESSAGE_PART_DELIMITER, offset) - offset;
                            if(logger.isLoggable(Level.FINEST)) logger.log(Level.FINEST, "onWampMessage (text): " + message);
                            WampList request = (WampList)WampEncoding.JSON.getSerializer().deserialize(message, offset, partLen);
                            wampApp.onWampMessage(clientSocket, request);
                            offset = offset + partLen + 1;  // MESSAGE_PART_DELIMITER
                        }
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
                        if(logger.isLoggable(Level.FINEST)) logger.log(Level.FINEST, "onWampMessage (text): " + message);
                        WampList request = (WampList)WampEncoding.JSON.getSerializer().deserialize(message, 0, message.length());
                        wampApp.onWampMessage(clientSocket, request);
                    } catch(Exception ex) { 
                        logger.log(Level.SEVERE, "Error processing message: "+message, ex);
                    }
                }

            });

        }
        
        
    }


    public void onWampClose(Session session, CloseReason reason) 
    {
        removeWampMessageHandlers(application, session, reason);
    }
    
    public static void removeWampMessageHandlers(WampApplication application, Session session, CloseReason reason) 
    {
        WampSocket clientSocket = sockets.remove(session.getId());
        if(clientSocket != null) {
            application.onWampClose(clientSocket, reason);
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
        List<String> subprotocols = java.util.Arrays.asList("wamp.2.json", "wamp.2.msgpack", "wamp.2.json.batched", "wamp.2.msgpack.batched");
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
    
 
    @Override
    public void modifyHandshake(ServerEndpointConfig config, 
                                HandshakeRequest request, 
                                HandshakeResponse response)
    {
        String wampAuthId = null;
        String wampCookieValue = null;
        List<String> setCookieHeaders = request.getHeaders().get("Cookie");
        if(setCookieHeaders != null) {
            for(String cookies : setCookieHeaders) {
                StringTokenizer st = new StringTokenizer(cookies, ";");
                while(st.hasMoreTokens()) {
                    String token  = st.nextToken();
                    String name = token.substring(0, token.indexOf("="));
                    String value = token.substring(token.indexOf("=")+1, token.length());
                    System.out.println("Cookie received: " + name  + "=" + value);

                    if(name.equalsIgnoreCase(WAMP_AUTH_COOKIE_NAME)) {
                        if(verifyWampCookie(value)) {
                            wampCookieValue = value;
                            wampAuthId = extractAuthIdFromWampCookie(value);
                        }
                    }
                }
            }
        }
    
        if(wampCookieValue == null) {
            wampAuthId = String.valueOf(WampProtocol.newId());
            wampCookieValue = signWampCookie(wampAuthId);
            
            List<String> cookies = response.getHeaders().get("Cookie");
            if(cookies == null) cookies = new ArrayList<String>();
            
            cookies.add(WAMP_AUTH_COOKIE_NAME +"=" + wampCookieValue);
            response.getHeaders().put("Set-Cookie", cookies);
        }

        
        Map<String,Object> configProps = config.getUserProperties();
        configProps.put(WampCRA.WAMP_AUTH_ID_PROPERTY_NAME, wampAuthId);
        configProps.put(WAMP_ENDPOINTCONFIG_PROPERTY_NAME, this);
        configProps.put(WAMP_APPLICATION_PROPERTY_NAME, application);
        
    } 
    
    private String getSignatureKey()
    {
        // TODO: use configurable key
        String configKey = "qksij,3kdi8987,xz<870+poiu9887fffqqqqw";
        return configKey;
    }
    
    private String signWampCookie(String value)
    {
        return value + ":" + String.valueOf(Math.abs((value + getSignatureKey()).hashCode()));
    }
    
    private String extractAuthIdFromWampCookie(String cookieValue) 
    {
        int pos = cookieValue.indexOf(":");        
        if(pos == -1) {
            return null;
        } else {
            cookieValue = cookieValue.substring(0, pos);
            return cookieValue;
        }
    }
    
    private boolean verifyWampCookie(String value)
    {
        if(value == null) {
            return false;
        } else {
            return value.equals(signWampCookie(extractAuthIdFromWampCookie(value)));
        }
    }

    
}