package org.wgs.wamp.jms;

import java.util.logging.Level;
import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.WebSocketContainer;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.transport.http.websocket.WampEndpointConfig;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampObject;


public class WampTopicConnection extends Endpoint implements TopicConnection
{
    private boolean open;
    private boolean startDelivery;
    private WebSocketContainer con;
    private WampApplication wampApp;
    private WampSocket clientSocket;
    
    public WampTopicConnection(WampTopicConnectionFactory factory, String userName, String password) throws JMSException
    {
        this.open = false;
        this.con = ContainerProvider.getWebSocketContainer();        
        this.wampApp = new WampApplication(WampApplication.WAMPv2, null);
        
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().preferredSubprotocols(java.util.Arrays.asList("wamp.2.json")).build();
        try {
            con.connectToServer(this, config, factory.getURI());
        } catch(Exception e) {
            System.err.println("Error: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            throw new JMSException("Unable to connect to server: " + e.getMessage());
        }
    }
    
    public WampApplication getWampApplication()
    {
        return wampApp;
    }
    
    public WampSocket getWampSocket()
    {
        return clientSocket;
    }
    
    @Override
    public void onOpen(javax.websocket.Session session, EndpointConfig config) {
        this.clientSocket = new WampSocket(wampApp, session);
        WampEndpointConfig.addWampMessageHandlers(wampApp, session);
    }    
    
    @Override
    public void onClose(javax.websocket.Session session, CloseReason reason) 
    {
        WampEndpointConfig.removeWampMessageHandlers(wampApp, session, reason);
        super.onClose(session, reason);
    }
        

    @Override
    public TopicSession createTopicSession(boolean transacted, int acknowledgeMode) throws JMSException {
        return new WampTopicSession(this, transacted, acknowledgeMode);
    }

    @Override
    public ConnectionConsumer createConnectionConsumer(Topic topic, String string, ServerSessionPool ssp, int i) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ConnectionConsumer createDurableConnectionConsumer(Topic topic, String string, String string1, ServerSessionPool ssp, int i) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Session createSession(boolean bln, int i) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Session createSession(int i) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Session createSession() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String getClientID() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setClientID(String string) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ConnectionMetaData getMetaData() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ExceptionListener getExceptionListener() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setExceptionListener(ExceptionListener el) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    
    public boolean isStarted() {
        return startDelivery;
    }
    
    public void waitDeliveryStart() throws Exception
    {
        synchronized(this) {
            while(!startDelivery) this.wait();
        }
    }
    
    @Override
    public void start() throws JMSException {
        synchronized(this) {
            startDelivery = true;
            this.notifyAll();
        }
    }

    @Override
    public void stop() throws JMSException {
        synchronized(this) {
            startDelivery = false;
            this.notifyAll();
        }
    }

    @Override
    public void close() throws JMSException {
        open = false;
        stop();
        this.clientSocket.close(null);
    }
    
    public boolean isOpen()
    {
        return open;
    }

    @Override
    public ConnectionConsumer createConnectionConsumer(Destination dstntn, String string, ServerSessionPool ssp, int i) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ConnectionConsumer createSharedConnectionConsumer(Topic topic, String string, String string1, ServerSessionPool ssp, int i) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ConnectionConsumer createSharedDurableConnectionConsumer(Topic topic, String string, String string1, ServerSessionPool ssp, int i) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


    
}
