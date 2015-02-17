package org.wgs.wamp.transport.http.websocket;

import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.Map;
import javax.websocket.CloseReason;
import javax.websocket.Session;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.type.WampConnectionState;


public class WampWebsocket extends WampSocket
{
    private Session session;
    
    public WampWebsocket(Session session) 
    {
        this.session = session;
        setUserPrincipal(this.session.getUserPrincipal());
    }
    
    
    @Override
    public Object getSessionData(String key) 
    {
        return session.getUserProperties().get(key);
    }

    @Override
    public void putSessionData(String key, Object val) 
    {
        session.getUserProperties().put(key, val);
    }    
    
    @Override
    public Object removeSessionData(String key) 
    {
        return session.getUserProperties().remove(key);
    }      
    
    @Override
    public boolean containsSessionData(String key) 
    {
        return session.getUserProperties().containsKey(key);
    }       

    @Override
    public String getNegotiatedSubprotocol()
    {
        return session.getNegotiatedSubprotocol();
    }
    
    @Override
    public void sendObject(Object msg) 
    {
        try {
            if(isOpen()) {
                switch(getEncoding()) {
                    case JSON:
                        session.getBasicRemote().sendText(msg.toString());
                        break;
                    case MsgPack:
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap((byte[])msg));
                        break;
                    default:
                        session.getBasicRemote().sendObject(msg);
                }
            }

        } catch(Exception e) {
            //close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "wamp.close.error"));
        }
    }
    
    
    @Override
    public boolean close(CloseReason reason)
    {
        if(super.close(reason)) {
            try { session.close(reason); } 
            catch(Exception ex) { }    
            return true;
        }
        return false;
    }
    
    
    @Override
    public boolean isOpen() 
    {
        return super.isOpen() && session.isOpen();
    }    
    
    
}
