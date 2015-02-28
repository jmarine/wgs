package org.wgs.wamp.jms;

import java.util.Collections;
import java.util.Enumeration;
import javax.jms.Destination;
import javax.jms.JMSException;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.topic.JmsServices;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


public class WampMessage implements javax.jms.TextMessage
{
    private WampDict props;
    private WampList payload;
    private WampDict payloadKw;
    
    public WampMessage()
    {
        this.props = new WampDict();
    }

    public WampMessage(Long id, WampDict details, WampList payload, WampDict payloadKw) throws Exception
    {
        this.props = details;
        if(props == null) props = new WampDict();
        
        setJMSMessageID(id.toString());
        this.payload = payload;
        this.payloadKw = payloadKw;
    }

    
    public WampDict getDetails()
    {
        return props;
    }
    
    @Override
    public String getJMSMessageID() throws JMSException {
        return props.getText("_jms_msgid");
    }

    @Override
    public void setJMSMessageID(String strID) throws JMSException {
        props.put("_jms_msgid", strID);
    }

    @Override
    public long getJMSTimestamp() throws JMSException {
        return props.getLong("_jms_timestamp");
    }

    @Override
    public void setJMSTimestamp(long t) throws JMSException {
        props.put("_jms_timestamp", new Long(t));
    }

    @Override
    public byte[] getJMSCorrelationIDAsBytes() throws JMSException {
        return getJMSCorrelationID().getBytes();
    }

    @Override
    public void setJMSCorrelationIDAsBytes(byte[] bytes) throws JMSException {
        setJMSCorrelationID(new String(bytes));
    }

    @Override
    public void setJMSCorrelationID(String correlation) throws JMSException {
        props.put("_jms_correlationid", correlation);
    }

    @Override
    public String getJMSCorrelationID() throws JMSException {
        return props.getText("_jms_correlationid");
    }

    @Override
    public Destination getJMSReplyTo() throws JMSException {
        return WampBroker.getTopic(props.getText("_jms_replyto"));
    }

    @Override
    public void setJMSReplyTo(Destination destination) throws JMSException {
        props.put("_jms_replyto", destination.toString());
    }

    @Override
    public Destination getJMSDestination() throws JMSException {
        return WampBroker.getTopic(props.getText("_jms_destination"));
    }

    @Override
    public void setJMSDestination(Destination destination) throws JMSException {
        props.put("_jms_destination", destination.toString());
    }

    @Override
    public int getJMSDeliveryMode() throws JMSException {
        return props.getLong("_jms_delivery_mode").intValue();
    }

    @Override
    public void setJMSDeliveryMode(int deliveryMode) throws JMSException {
        props.put("_jms_delivery_mode", deliveryMode);
    }

    @Override
    public boolean getJMSRedelivered() throws JMSException {
        return props.getBoolean("_jms_redelivered");
    }

    @Override
    public void setJMSRedelivered(boolean redelivered) throws JMSException {
        props.put("_jms_redelivered", redelivered);
    }

    @Override
    public String getJMSType() throws JMSException {
        return props.getText("_jms_type");
    }

    @Override
    public void setJMSType(String type) throws JMSException {
        props.put("_jms_type", type);
    }

    @Override
    public long getJMSExpiration() throws JMSException {
        return props.getLong("_jms_expiration");
    }

    @Override
    public void setJMSExpiration(long expiration) throws JMSException {
        props.put("_jms_expiration", expiration);
    }

    @Override
    public long getJMSDeliveryTime() throws JMSException {
        return props.getLong("_jms_delibery_type");
    }

    @Override
    public void setJMSDeliveryTime(long deliveryTime) throws JMSException {
        props.put("_jms_delibery_type", deliveryTime);
    }

    @Override
    public int getJMSPriority() throws JMSException {
        return props.getLong("_jms_priority").intValue();
    }

    @Override
    public void setJMSPriority(int priority) throws JMSException {
        props.put("_jms_priority", priority);
    }

    @Override
    public void clearProperties() throws JMSException {
        props.clear();
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
        return props.getLong(propName).byteValue();
    }

    @Override
    public short getShortProperty(String propName) throws JMSException {
        return props.getLong(propName).shortValue();
    }

    @Override
    public int getIntProperty(String propName) throws JMSException {
        return props.getLong(propName).intValue();
    }

    @Override
    public long getLongProperty(String propName) throws JMSException {
        return props.getLong(propName);
    }

    @Override
    public float getFloatProperty(String propName) throws JMSException {
        return props.getDouble(propName).floatValue();
    }

    @Override
    public double getDoubleProperty(String propName) throws JMSException {
        return props.getDouble(propName).doubleValue();
    }

    @Override
    public String getStringProperty(String propName) throws JMSException {
        return props.getText(propName);
    }

    @Override
    public Object getObjectProperty(String propName) throws JMSException {
        return props.get(propName);
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

    @Override
    public void setText(String payload) throws JMSException {
        if(payload == null) {
            this.payload = null;
            this.payloadKw = null;
        } else {
            try {
                WampList list = (WampList)WampEncoding.JSON.getSerializer().deserialize(payload, 0, payload.length());
                this.payload = (WampList)list.get(0);
                this.payloadKw = (WampDict)list.get(1);
            } catch(Exception ex) {
                throw new JMSException("ERROR: " + ex.getMessage());
            }
        }
    }

    @Override
    public String getText() throws JMSException {
        try {
            WampList event = new WampList();
            event.add(payload);
            event.add(payloadKw);        
            return (String)WampEncoding.JSON.getSerializer().serialize(event);
        } catch(Exception ex) {
            throw new JMSException("ERROR: " + ex.getMessage());
        }
    }
    
    @Override
    public String toString() {
        try {
            return getText();
        } catch(Exception ex) {
            throw new RuntimeException("WampMessage.toString: ERROR: " + ex.getClass() + ": " + ex.getMessage(), ex);
        }
    }
    
}
