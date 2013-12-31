package org.wgs.wamp;

import java.util.Iterator;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;


public class WampObject 
{
    public enum Type { string, integer, id, uri, dict, list };
    
    protected Type t;
    protected Object v;


    public int asInt()
    {
        return (int)v;
    }
    
    public Long asId()
    {
        return (Long)v;
    }    
    
    public String asText()
    {
        return (String)v;
    }        
    
    public boolean asBoolean()
    {
        return ((int)v) != 0;
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
        if(obj instanceof Long) {
            retval = new WampObject();
            retval.setObject(obj, WampObject.Type.id);
        } else if(obj instanceof Integer) {
            retval = new WampObject(); 
            retval.setObject(obj, WampObject.Type.integer);
        } else if(obj instanceof String) {
            retval = new WampObject(); 
            retval.setObject(obj, WampObject.Type.string);
        } else if(obj instanceof Boolean) {
            retval = new WampObject();
            retval.setObject(((Boolean)obj).booleanValue()? 1 : 0, WampObject.Type.integer);
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
