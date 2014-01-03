package org.wgs.wamp;


public class WampObject 
{
    public enum Type { string, bool, integer, real, dict, list };
    
    protected Type t;
    protected Object v;

    
    public Long asLong()
    {
        return (Long)v;
    }
    
    public Double asDouble()
    {
        return (Double)v;
    }    

    public String asText()
    {
        return (String)v;
    }        
    
    public Boolean asBoolean()
    {
        return (Boolean)v;
    }
    
    public Type getType()
    {
        return t;
    }
    
    public Object getObject()
    {
        return v;
    }
    
    public void setObject(Object obj, Type t)
    {
        this.v = obj;
        this.t = t;
    }
    

    public WampObject castToWampObject(Object obj) 
    {
        WampObject retval = null;
        if(obj instanceof Integer) {
            retval = new WampObject(); 
            retval.setObject(((Integer)obj).longValue(), WampObject.Type.integer);
        } else if(obj instanceof Long) {
            retval = new WampObject();
            retval.setObject(obj, WampObject.Type.integer);
        } else if(obj instanceof Double) {
            retval = new WampObject(); 
            retval.setObject(obj, WampObject.Type.real);
        } else if(obj instanceof Float) {
            retval = new WampObject();
            retval.setObject(((Float)obj).doubleValue(), WampObject.Type.real);
        } else if(obj instanceof String) {
            retval = new WampObject(); 
            retval.setObject(obj, WampObject.Type.string);
        } else if(obj instanceof Boolean) {
            retval = new WampObject();
            retval.setObject(obj, WampObject.Type.bool);
        } else {
            retval = (WampObject)obj;
        }
        return retval;
    }    
    

    public static WampSerializer getSerializer(WampEncoding encoding)
    {
        switch(encoding) {
            case JSon:
                return new WampSerializerJSON();
            case MsgPack:
                return new WampSerializerMsgPack();
            default:
                return null;
        }
    }
    
}
