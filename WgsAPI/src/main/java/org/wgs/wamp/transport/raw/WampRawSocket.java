package org.wgs.wamp.transport.raw;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import javax.net.SocketFactory;
import jakarta.websocket.CloseReason;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampException;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.type.WampList;


public class WampRawSocket extends WampSocket implements Runnable
{
    private static final int DEFAULT_PORT = 8080;
    
    private boolean handshake;
    private WampApplication app;
    private Socket socket;
    private InputStream is;
    private OutputStream os;
    private HashMap<String,Object> sessionData = new HashMap<String,Object>();
    private Thread listener;
    private WampEncoding preferredEncoding;
    private long maxMsgLen;
    

    public WampRawSocket(WampApplication app, URI url, WampEncoding enc) throws Exception
    {
        this.app = app;
        this.preferredEncoding = enc;
        
        int port = url.getPort();
        if(port == 0) port = DEFAULT_PORT;
        
        SocketFactory sf = null;
        if(url.getScheme().equals("ssl")) {
            sf = javax.net.ssl.SSLSocketFactory.getDefault();
        } else {
            sf = javax.net.SocketFactory.getDefault();
        }
        
        socket = sf.createSocket(url.getHost(), port);
        
        listener = new Thread(this);
        listener.start();
        
        synchronized(listener) {
            while(!handshake) {
                listener.wait();
            }
        }

    }
    
    
    private byte getRawSocketEncoding(WampEncoding enc)
    {
        switch(enc) {
            case JSON:
            case BatchedJSON:
                return 1;
                
            case MsgPack:
            case BatchedMsgPack:
                return 2;
            
            default:
                return 0;
        }
    }
    
    
    @Override
    public void run() {
        try { 
            byte[] msg = new byte[256*256];
            
            is = socket.getInputStream();
            os = socket.getOutputStream();
            

            // WAMP V2:
            msg[0] = 0x7F;  // WAMP V2 magic number
            msg[1] = (byte)(0x70 | getRawSocketEncoding(preferredEncoding)); // Max message length 2^16 + preferred encoding
            msg[2] = 0x00;
            msg[3] = 0x00;
            os.write(msg, 0, 4);  // start handshake
            os.flush();

            int len = is.read(msg, 0, 4);  // read bufflen and encoding
            if(len == 4) {
                maxMsgLen = (long) Math.pow(2, (msg[1] >> 4 & 0x0F) + 9);
                switch(msg[1] & 0x0F) {
                    case 1: 
                        setEncoding(WampEncoding.JSON);
                        break;
                    default:
                        setEncoding(WampEncoding.MsgPack);
                        break;
                }
            }
            

            this.init();
            synchronized(listener) {
                handshake = true;
                listener.notifyAll();
            }
            
            while( isOpen() && (len = is.read(msg,0,4)) == 4 ) {
                len = ntohs(msg, 0, 4);
                len = is.read(msg, 0, len);
                WampList request = null;
                switch(getEncoding()) {
                    case JSON:
                        String json = new String(msg, 0, len, StandardCharsets.UTF_8);
                        request = (WampList)getEncoding().getSerializer().deserialize(json, 0, json.length());
                        break;
                    default:
                        request = (WampList)getEncoding().getSerializer().deserialize(msg, 0, len);
                        break;
                }
                app.onWampMessage(this, request);
            }
            
            is.close();
            os.close();
                
        } catch(Exception ex) {
            if(isOpen()) System.out.println("WampRawSocket::run: ERROR: " + ex.getMessage());
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
    public void sendObject(Object msg) throws Exception
    {
        byte[] buf = null;
        switch(getEncoding()) {
            case JSON:
                buf = msg.toString().getBytes(StandardCharsets.UTF_8);
                break;
            default:
                buf = (byte[])msg;
                break;
        }

        if(buf.length > maxMsgLen) {
            throw new WampException(null, "wgs.error.rawsocket_send_message_overflow", null, null);
        } else {
            os.write(htons(buf.length));
            os.write(buf, 0, buf.length);
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
        switch(getEncoding()) {
            case JSON:
                return "wamp.2.json";
            default:
                return "wamp.2.msgpack";
        }
    }

    @Override
    public Object getSessionData(String key) {
        return sessionData.get(key);
    }

    @Override
    public void putSessionData(String key, Object val) {
        sessionData.put(key, val);
    }

    @Override
    public Object removeSessionData(String key) {
        return sessionData.remove(key);
    }

    @Override
    public boolean containsSessionData(String key) {
        return sessionData.containsKey(key);
    }

    
}
