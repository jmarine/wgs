package org.wgs.wamp.encoding;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.logging.Level;

import org.msgpack.MessagePack;
import org.msgpack.packer.BufferPacker;
import org.msgpack.type.ArrayValue;
import org.msgpack.type.BooleanValue;
import org.msgpack.type.FloatValue;
import org.msgpack.type.IntegerValue;
import org.msgpack.type.MapValue;
import org.msgpack.type.NilValue;
import org.msgpack.type.RawValue;
import org.msgpack.type.Value;
import org.msgpack.unpacker.Unpacker;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampObject;


public class WampSerializerBatchedMsgPack extends WampSerializerMsgPack
{
    @Override
    public Object serialize(WampObject obj) throws Exception 
    {
        byte[] data = (byte[])super.serialize(obj);
        int dataLen = data.length;
        
        ByteBuffer bb = ByteBuffer.allocate(dataLen+4);
        bb.putInt(dataLen);
        bb.put(data);
        
        return bb.array();
    }

 
    
    @Override
    public WampObject deserialize(Object obj, int offset, int len) throws Exception 
    {
        WampList list = new WampList();        
        byte[] message = (byte[])obj;
        while(offset < len) {
            int partLen = java.nio.ByteBuffer.wrap(message, offset, 4).getInt();                            
            WampList part = (WampList)WampEncoding.MsgPack.getSerializer().deserialize(message, offset+4, partLen);
            list.add(part);
            offset = offset + 4 + partLen;            
        }
        return list;
    }
    
    
    
}
