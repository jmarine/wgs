package org.wgs.wamp.transport.raw;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import javax.net.SocketFactory;
import javax.websocket.CloseReason;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.type.WampList;


public class WampRawSocket extends WampSocket implements Runnable
{
    private static final int DEFAULT_PORT = 8080;
    
    private URI url;
    private WampApplication app;
    private Socket socket;
    private InputStream is;
    private OutputStream os;
    private HashMap<String,Object> sessionData = new HashMap<String,Object>();
    

    public WampRawSocket(WampApplication app, URI url) throws Exception
    {
        setEncoding(WampEncoding.MsgPack);
        
        this.app = app;
        this.url = url;
        
        int port = url.getPort();
        if(port == 0) port = DEFAULT_PORT;
        
        SocketFactory sf = null;
        if(url.getScheme().equals("ssl")) {
            sf = javax.net.ssl.SSLSocketFactory.getDefault();
        } else {
            sf = javax.net.SocketFactory.getDefault();
        }
        
        socket = sf.createSocket(url.getHost(), port);
        
        Thread listener = new Thread(this);
        listener.start();
    }
    
    
    @Override
    public void run() {
        try { 
            int len = 0;
            byte[] msg = new byte[256*256];
            
            is = socket.getInputStream();
            os = socket.getOutputStream();

            /*
            // WAMP V2:
            msg[0] = 0x7F;
            msg[1] = 0x72;
            msg[2] = 0x00;
            msg[3] = 0x00;
            os.write(msg, 0, 4);  // WAMP V2 magic number, Max message length 2^16 and MsgPack
            os.flush();

            int len = is.read(msg, 0, 4);  // read bufflen and encoding
            if(len == 4) {
                switch(msg[1] & 0x0F) {
                    case 1: 
                        setEncoding(WampEncoding.JSON);
                        break;
                    default:
                        setEncoding(WampEncoding.MsgPack);
                        break;
                }
            }
            */
            
            
            while( isOpen() && (len = is.read(msg,0,4)) == 4 ) {
                len = ntohs(msg, 0, 4);
                len = is.read(msg, 0, len);
                WampList request = (WampList)WampEncoding.MsgPack.getSerializer().deserialize(msg, 0, msg.length);
                app.onWampMessage(this, request);
            }
            
            is.close();
            os.close();
                
        } catch(Exception ex) {
            
        }
        
    }
    
    private int ntohs(byte[] buff, int offset, int len)
    {
        return ByteBuffer.wrap(buff, offset, len).getInt();
    }
    
    private byte[] htons(int i2send)
    {
        byte buffer[] = new byte[4];
        return ByteBuffer.wrap(buffer).putInt(i2send).array();
    }
    
    
    @Override
    public void sendObject(Object msg) 
    {
        try {
            if(isOpen()) {
                byte[] buf = null;
                switch(getEncoding()) {
                    case JSON:
                        buf = msg.toString().getBytes("UTF-8");
                        break;
                    case MsgPack:
                        buf = (byte[])msg;
                        break;
                    default:
                        // not supported.
                }
                
                os.write(htons(buf.length));
                os.write(buf, 0, buf.length);
            }

        } catch(Exception e) {
            //close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "wamp.close.error"));
        }
    }    

    

    
    @Override
    public boolean close(CloseReason reason)
    {
        if(super.close(reason)) {
            try { socket.close(); }
            catch(Exception ex) { }
            return true;
        } else {
            return false;
        }
    }    
    
    @Override
    public String getNegotiatedSubprotocol() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Object getSessionData(String key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void putSessionData(String key, Object val) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Object removeSessionData(String key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean containsSessionData(String key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.

    
    }

    
}
