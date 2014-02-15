package org.wgs.wamp.jms;

import java.net.URI;
import javax.jms.Connection;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;


public class WampTopicConnectionFactory implements TopicConnectionFactory
{
    private URI uri;
    
    public WampTopicConnectionFactory(URI uri)
    {
        this.uri = uri;
    }

    public URI getURI()
    {
        return uri;
    }
    
    @Override
    public TopicConnection createTopicConnection() throws JMSException {
        return new WampTopicConnection(this, null, null);
    }

    @Override
    public TopicConnection createTopicConnection(String userName, String password) throws JMSException {
        return new WampTopicConnection(this, userName, password);
    }

    @Override
    public Connection createConnection() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Connection createConnection(String userName, String password) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public JMSContext createContext() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public JMSContext createContext(String string, String string1) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public JMSContext createContext(String string, String string1, int i) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public JMSContext createContext(int i) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
