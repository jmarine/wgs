package org.wgs.wamp.jms;

import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
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
import org.wgs.wamp.client.WampClient;
import org.wgs.wamp.encoding.WampEncoding;


public class WampTopicConnection implements TopicConnection
{
    private String  realm;
    private String  user;
    private String  password;
    private boolean digestPasswordMD5;
    private boolean startDelivery;
    private WampClient client;
    
    private Stack<WampTopicSubscriber> pendingSubscriptionRequests = new Stack<WampTopicSubscriber>();
    
    
    public WampTopicConnection(WampTopicConnectionFactory factory, String userName, String password) throws JMSException
    {
        try {
            this.realm = factory.getRealm();
            this.digestPasswordMD5 = factory.getDigestPasswordMD5();
            this.user = userName;
            this.password = password;
            
            client = new WampClient(factory.getURL());
            client.setPreferredWampEncoding(factory.getWampEncoding());
            
        } catch(Exception e) {
            System.err.println("Error: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            throw new JMSException("Unable to connect to server: " + e.getMessage());
        }
    }
    
    public WampClient getWampClient()
    {
        return client;
    }
    
    
    public void requestSubscription(WampTopicSubscriber subscriber) throws JMSException
    {
        if(isStarted()) subscriber.start();
        else pendingSubscriptionRequests.add(subscriber);
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
        synchronized(this) {
            return startDelivery;
        }
    }
    
    public void waitDeliveryStart() throws Exception
    {
        synchronized(this) {
            while(!startDelivery) this.wait();
        }
    }
    
    @Override
    public void start() throws JMSException {
        try {
            synchronized(this) {
                startDelivery = true;
                this.notifyAll();
            }

            connect();
            while(pendingSubscriptionRequests.size() > 0) {
                try {
                    WampTopicSubscriber subscriber = pendingSubscriptionRequests.pop();
                    subscriber.start();
                } catch(Exception ex) {
                    System.err.println("Error: " + ex.getClass().getName() + ": " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
            
            client.waitResponses();
            
        } catch(Exception ex) {
            throw new JMSException(ex.getMessage());
        }
    }
    
    public void connect() throws Exception
    {
        if(!isOpen()) {
            client.connect();
            client.hello(realm, user, password, digestPasswordMD5);
            client.waitResponses();
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
        stop();
        try { 
            client.waitResponses();
            this.client.close(); 
        } catch(Exception ex) { 
            throw new JMSException("Unable to close: " + ex.getMessage()); 
        }
    }
    
    public boolean isOpen()
    {
        return client.isOpen();
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
