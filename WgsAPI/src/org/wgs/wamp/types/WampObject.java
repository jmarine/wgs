package org.wgs.wamp.types;

import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.WampException;
import org.wgs.wamp.encoding.WampSerializer;
import org.wgs.wamp.encoding.WampSerializerJSON;
import org.wgs.wamp.encoding.WampSerializerMsgPack;


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
    

    public static WampSerializer getSerializer(WampEncoding encoding) throws WampException
    {
        switch(encoding) {
            case JSon:
                return new WampSerializerJSON();
            case MsgPack:
                return new WampSerializerMsgPack();
            default:
                throw new WampException(null, "wamp.error.unsupported_encoding", null, null);
        }
    }
    
}
