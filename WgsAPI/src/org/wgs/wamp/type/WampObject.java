package org.wgs.wamp.type;

import java.util.Collection;
import java.util.Map;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.WampException;
import org.wgs.wamp.encoding.WampSerializer;
import org.wgs.wamp.encoding.WampSerializerJSON;
import org.wgs.wamp.encoding.WampSerializerBatchedJSON;
import org.wgs.wamp.encoding.WampSerializerMsgPack;
import org.wgs.wamp.encoding.WampSerializerBatchedMsgPack;


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
        } else if(obj instanceof Map) {
            WampDict dict = new WampDict();
            Map map = (Map)obj;
            for(Object key : map.keySet()) {
                dict.put(key.toString(), castToWampObject(map.get(key)));
            }
            return dict;
        } else if(obj instanceof Collection) {
            WampList list = new WampList();
            Collection col = (Collection)obj;
            for(Object item : col) {
                list.add(castToWampObject(item));
            }
            return list;
        } else {
            return obj;
        }
    }    
    

    
    
}
