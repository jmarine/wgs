package org.wgs.wamp.jms;

import javax.jms.Connection;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import org.wgs.wamp.encoding.WampEncoding;


public class WampTopicConnectionFactory implements TopicConnectionFactory
{
    private WampEncoding enc;
    private String url;
    private String realm;
    private boolean digestPasswordMD5;
    
    
    public WampTopicConnectionFactory(WampEncoding enc, String url, String realm, boolean digestPasswordMD5)
    {
        this.url = url;
        this.enc = enc;
        this.realm = realm;
        this.digestPasswordMD5 = digestPasswordMD5;
    }

    public String getURL()
    {
        return url;
    }
    
    public String getRealm()
    {
        return realm;
    }
    
    public WampEncoding getWampEncoding()
    {
        return enc;
    }
    
    public boolean getDigestPasswordMD5()
    {
        return digestPasswordMD5;
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
