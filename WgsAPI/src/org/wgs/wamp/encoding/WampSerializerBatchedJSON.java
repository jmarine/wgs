package org.wgs.wamp.encoding;

import org.wgs.wamp.type.WampObject;
import org.wgs.wamp.type.WampList;


public class WampSerializerBatchedJSON extends WampSerializerJSON
{
    public static final char MESSAGE_PART_DELIMITER = 0x1e;
    
    @Override
    public Object serialize(WampObject obj) throws Exception {
        String str = (String)super.serialize(obj);
        str = str + (char)0x1e;
        return str;
    }
    
    @Override
    public WampObject deserialize(Object obj, int offset, int len) throws Exception 
    {
        WampList list = new WampList();
        String message = (String)obj;
        while(offset < len) {
            int partLen = message.indexOf(MESSAGE_PART_DELIMITER, offset) - offset;
            WampList part = (WampList)WampEncoding.JSON.getSerializer().deserialize(message, offset, partLen);
            list.add(part);
            offset = offset + partLen + 1;  // MESSAGE_PART_DELIMITER
        }
        return list;
    }
    
}
