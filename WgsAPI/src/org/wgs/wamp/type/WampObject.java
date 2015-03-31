package org.wgs.wamp.type;

import java.util.Collection;
import java.util.Iterator;
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
            return ((Integer)obj).longValue();
        } else if(obj instanceof Float) {
            return ((Float)obj).doubleValue();
        } else if(obj instanceof Map) {
            WampDict dict = new WampDict();
            Map map = (Map)obj;
            Iterator iterator = map.entrySet().iterator();
            while(iterator.hasNext()) {
                Map.Entry mapEntry = (Map.Entry) iterator.next();
                dict.put(mapEntry.getKey().toString(), castToWampObject(mapEntry.getValue()));
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
