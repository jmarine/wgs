package org.wgs.wamp;


public class WampObject 
{
    public Object castToWampObject(Object obj) 
    {
        WampObject retval = null;
        if(obj == null) {
            return null;
        } else if(obj instanceof Integer) {
            return new Long(((Integer)obj).longValue());
        } else if(obj instanceof Float) {
            return new Double(((Float)obj).doubleValue());
        } else {
            return obj;
        }
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
