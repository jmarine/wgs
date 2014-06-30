
package org.wgs.wamp.client;

import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.WebSocketContainer;
import org.wgs.security.WampCRA;
import org.wgs.util.HexUtils;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.rpc.WampAsyncCallback;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampObject;


public class WampClient extends Endpoint 
{
    private static final Logger logger = Logger.getLogger(WampClient.class.getName());    
    
    private URI uri;
    private String authid;
    private String password;
    private boolean open;
    private String realm;
    private WampEncoding preferredEncoding;
    private WampEncoding encoding;
    private WebSocketContainer con;
    private WampApplication wampApp;
    private WampSocket clientSocket;
    private AtomicInteger taskCount;
    
    private ConcurrentHashMap<Long, List> pendingRequests;
    
        
    public WampClient(String uri) throws Exception
    {
        this.uri = new URI(uri);
        this.preferredEncoding = WampEncoding.JSon;
        this.pendingRequests = new ConcurrentHashMap<Long, List>();
        this.taskCount = new AtomicInteger(0);
        
        this.wampApp = new WampApplication(WampApplication.WAMPv2, null) {
            @Override
            public void onWampMessage(WampSocket clientSocket, WampList response) throws Exception
            {
                Long responseType = response.getLong(0);
                //logger.log(Level.INFO, "Request type = {0}", new Object[]{requestType});

                switch(responseType.intValue()) {
                    case WampProtocol.ABORT:
                        removePendingMessage(null);
                        break;
                    case WampProtocol.CHALLENGE:
                        String authMethod = response.getText(1);
                        WampDict challengeDetails = (WampDict)response.get(2);
                        
                        onWampChallenge(clientSocket, authMethod, challengeDetails);
                        
                        if(authMethod.equalsIgnoreCase("wampcra") && WampClient.this.password != null) {
                            MessageDigest md5 = MessageDigest.getInstance("MD5");
                            String passwordMD5 = HexUtils.byteArrayToHexString(md5.digest(WampClient.this.password.getBytes("UTF-8")));
                            String challenge = challengeDetails.getText("authchallenge");
                            String signature = WampCRA.authSignature(challenge, passwordMD5, challengeDetails);
                            WampProtocol.sendAuthenticationMessage(clientSocket, signature, null);                            
                        }
                        
                        break;
                    case WampProtocol.WELCOME:
                        WampDict welcomeDetails = (response.size() > 2) ? (WampDict)response.get(2) : null;
                        clientSocket.setSessionId(response.getLong(1));
                        onWampWelcome(clientSocket, welcomeDetails);
                        removePendingMessage(null);
                        break;                
                    case WampProtocol.CALL_RESULT:
                        Long callResponseId = response.getLong(1);
                        List callRequestList = WampClient.this.pendingRequests.get(callResponseId);
                        WampAsyncCallback callback = (WampAsyncCallback)callRequestList.get(0);
                        WampDict callResultDetails = (WampDict)response.get(2);
                        WampList callResult = (response.size() > 3) ? (WampList)response.get(3) : null;
                        WampDict callResultKw = (response.size() > 4) ? (WampDict)response.get(4) : null;
                        if(callback != null) callback.resolve(callResponseId, callResultDetails, callResult, callResultKw);
                        if(callResultDetails == null || !callResultDetails.has("receive_progress") || !callResultDetails.getBoolean("receive_progress") ) {
                            removePendingMessage(callResponseId);
                        }
                        break;
                    case WampProtocol.ERROR:
                        Long errorResponseId = response.getLong(1);
                        removePendingMessage(errorResponseId);
                        break;
                    default:
                        super.onWampMessage(clientSocket, response);
                }
                
            }
            
            public void onWampChallenge(WampSocket clientSocket, String authMethod, WampDict details) 
            {
                for(WampModule module : wampApp.getWampModules()) {
                    try { 
                        module.onChallenge(clientSocket, authMethod, details); 
                    } catch(Exception ex) {
                        logger.log(Level.SEVERE, "Error with wamp challenge:", ex);
                    }
                }                
            }
            
            public void onWampWelcome(WampSocket clientSocket, WampDict details) 
            {
                WampClient.this.authid = details.getText("authid");
                for(WampModule module : wampApp.getWampModules()) {
                    try { 
                        module.onSessionEstablished(clientSocket, details); 
                    } catch(Exception ex) {
                        logger.log(Level.SEVERE, "Error with wamp challenge:", ex);
                    }
                }                
            }            

        };
    }
    
    public WampApplication getWampApplication()
    {
        return wampApp;
    }
    


    private List getPreferredSubprotocolOrder()
    {
        if(preferredEncoding != null && preferredEncoding == WampEncoding.MsgPack) {
            return java.util.Arrays.asList("wamp.2.msgpack", "wamp.2.json");
        } else {
            return java.util.Arrays.asList("wamp.2.json", "wamp.2.msgpack");
        }
    }
    
    private WampEncoding getWampEncodingByName(String subprotocol)
    {
        if(subprotocol != null && subprotocol.equals("wamp.2.msgpack")) {
            return WampEncoding.MsgPack;
        } else {
            return WampEncoding.JSon;
        }
    }
   

    
    @Override
    public void onOpen(javax.websocket.Session session, EndpointConfig config) 
    {
        this.encoding = getWampEncodingByName(session.getNegotiatedSubprotocol());
        this.clientSocket = new WampSocket(wampApp, session);

        if(encoding == WampEncoding.MsgPack) {
            session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                    @Override
                    public void onMessage(byte[] message) {
                        try {
                            System.out.println("onWampMessage (binary)");
                            WampList request = (WampList)WampObject.getSerializer(WampEncoding.MsgPack).deserialize(message);
                            System.out.println("onWampMessage (deserialized request): " + request);
                            wampApp.onWampMessage(clientSocket, request);
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
                            wampApp.onWampMessage(clientSocket, request);
                        } catch(Exception ex) { 
                            logger.log(Level.SEVERE, "Error processing message: "+message, ex);
                        }
                    }
                });
        }
        
    }    
    
    @Override
    public void onClose(javax.websocket.Session session, CloseReason reason) 
    {
        wampApp.onWampClose(clientSocket, reason);
        super.onClose(session, reason);
    }
        
    
    
    public void setPreferredWampEncoding(WampEncoding preferredEncoding)
    {
        this.preferredEncoding = preferredEncoding;
    }
    
    public void connect() throws Exception
    {
        this.open = false;
        this.con = ContainerProvider.getWebSocketContainer();        
        
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().preferredSubprotocols(getPreferredSubprotocolOrder()).build();
        con.connectToServer(this, config, uri);
    }
    
    public void goodbye(String reason)
    {
        WampProtocol.sendGoodBye(clientSocket, reason, null);
    }
    
    public void hello(String realm, String user, String password)    
    {
        WampDict authDetails = new WampDict();
        WampList authMethods = new WampList();
        
        if(user != null) {
            authDetails.put("authkey", user);
            authMethods.add("wampcra");
        }
        authMethods.add("anonymous");
        authDetails.put("authmethods", authMethods);

        this.password = password;
        hello(realm, authDetails);
    }
    
    public void hello(String realm, WampDict authDetails)
    {
        this.authid = null;
        createPendingMessage(null, null);
        WampProtocol.sendHelloMessage(clientSocket, realm, authDetails);
    }

    public void call(String procedureUri, WampList args, WampDict argsKw, WampDict options, WampAsyncCallback callback)
    {
        Long requestId = WampProtocol.newId();
        ArrayList list = new ArrayList();
        list.add(callback);
        createPendingMessage(requestId, list);
        WampProtocol.sendCallMessage(clientSocket, requestId, options, procedureUri, args, argsKw);
    }
    
   
    private void createPendingMessage(Long requestId, List requestData)
    {
        if(requestId != null) pendingRequests.put(requestId, requestData);
        taskCount.incrementAndGet();
    }
    
    private void removePendingMessage(Long requestId) {
        if(requestId != null) pendingRequests.remove(requestId);
        if(taskCount.decrementAndGet() <= 0) {
            synchronized(taskCount) { 
                taskCount.notifyAll();
            }
        }
    }
    
    public void waitPendingMessages() throws Exception
    {
        synchronized(taskCount) {
            taskCount.wait();
        }
    }
    

    public void close() throws Exception {
        open = false;
        this.clientSocket.close(null);
    }
    
    
}
