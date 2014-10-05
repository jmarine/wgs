package org.wgs.wamp.jms;

import java.util.Collections;
import java.util.Enumeration;
import javax.jms.Destination;
import javax.jms.JMSException;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


public class WampMessage implements javax.jms.Message
{
    private Long     id; 
    private WampDict props;
    private WampList payload;
    private WampDict payloadKw;
    
    public WampMessage()
    {
        this.props = new WampDict();
    }

    public WampMessage(Long id, WampDict details, WampList payload, WampDict payloadKw)
    {
        this.id = id;
        this.props = details;
        this.payload = payload;
        this.payloadKw = payloadKw;
    }

    public WampList getPayload()
    {
        return payload;
    }
    
    public WampDict getPayloadKw()
    {
        return payloadKw;
    }
    
    public WampDict getDetails()
    {
        return props;
    }
    
    @Override
    public String getJMSMessageID() throws JMSException {
        return id.toString();
    }

    @Override
    public void setJMSMessageID(String strID) throws JMSException {
        id = Long.parseLong(strID);
    }

    @Override
    public long getJMSTimestamp() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setJMSTimestamp(long l) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public byte[] getJMSCorrelationIDAsBytes() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setJMSCorrelationIDAsBytes(byte[] bytes) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setJMSCorrelationID(String string) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String getJMSCorrelationID() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Destination getJMSReplyTo() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setJMSReplyTo(Destination dstntn) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Destination getJMSDestination() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setJMSDestination(Destination dstntn) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getJMSDeliveryMode() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setJMSDeliveryMode(int i) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean getJMSRedelivered() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setJMSRedelivered(boolean bln) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String getJMSType() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setJMSType(String string) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public long getJMSExpiration() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setJMSExpiration(long l) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public long getJMSDeliveryTime() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setJMSDeliveryTime(long l) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getJMSPriority() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setJMSPriority(int i) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void clearProperties() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean propertyExists(String propName) throws JMSException {
        return props.has(propName);
    }

    @Override
    public boolean getBooleanProperty(String propName) throws JMSException {
        return props.getBoolean(propName);
    }

    @Override
    public byte getByteProperty(String propName) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public short getShortProperty(String propName) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getIntProperty(String propName) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public long getLongProperty(String propName) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public float getFloatProperty(String propName) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double getDoubleProperty(String propName) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String getStringProperty(String propName) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Object getObjectProperty(String propName) throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Enumeration getPropertyNames() throws JMSException {
        return Collections.enumeration(props.keySet());
    }

    @Override
    public void setBooleanProperty(String propName, boolean val) throws JMSException {
        props.put(propName, val);
    }

    @Override
    public void setByteProperty(String propName, byte val) throws JMSException {
        props.put(propName, val);
    }

    @Override
    public void setShortProperty(String propName, short val) throws JMSException {
        props.put(propName, val);
    }

    @Override
    public void setIntProperty(String propName, int val) throws JMSException {
        props.put(propName, val);
    }

    @Override
    public void setLongProperty(String propName, long val) throws JMSException {
        props.put(propName, val);
    }

    @Override
    public void setFloatProperty(String propName, float val) throws JMSException {
        props.put(propName, val);
    }

    @Override
    public void setDoubleProperty(String propName, double val) throws JMSException {
        props.put(propName, val);
    }

    @Override
    public void setStringProperty(String propName, String val) throws JMSException {
        props.put(propName, val);
    }

    @Override
    public void setObjectProperty(String propName, Object val) throws JMSException {
        props.put(propName, val);
    }

    @Override
    public void acknowledge() throws JMSException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void clearBody() throws JMSException {
        payload = null;
        payloadKw = null;
    }

    @Override
    public <T> T getBody(Class<T> type) throws JMSException {
        if(type.isAssignableFrom(WampList.class)) {
            return type.cast(payload);
        } else if(type.isAssignableFrom(WampDict.class)) {
            return type.cast(payloadKw);
        } else {
            throw new JMSException("WampMessage body is not assignable to " + type.getName());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isBodyAssignableTo(Class type) throws JMSException {
        return (payload != null && type.isAssignableFrom(WampList.class))
               || (payloadKw != null && type.isAssignableFrom(WampDict.class));
    }
    
}
