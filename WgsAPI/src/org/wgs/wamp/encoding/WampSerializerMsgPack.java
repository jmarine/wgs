package org.wgs.wamp.encoding;

import org.wgs.wamp.types.WampDict;
import org.wgs.wamp.types.WampObject;
import org.wgs.wamp.types.WampList;
import java.io.ByteArrayInputStream;
import java.util.Iterator;

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


public class WampSerializerMsgPack extends WampObject implements WampSerializer
{
    @Override
    public Object serialize(WampObject obj) throws Exception 
    {
        MessagePack msgpack = new MessagePack();
        BufferPacker packer = msgpack.createBufferPacker();
        write(packer, obj);
        byte[] bytes = packer.toByteArray();
        return bytes;
    }

    private void write(BufferPacker packer, Object obj) throws Exception
    {
        if(obj == null) {
            packer.writeNil();
        } else if(obj instanceof String) {
            packer.write((String)obj);
        } else if(obj instanceof Boolean) {
            packer.write((boolean)obj);
        } else if(obj instanceof Long) {
            packer.write((long)obj);
        } else if(obj instanceof Double) {
            packer.write((double)obj);
        } else if(obj instanceof WampDict) {                    
            WampDict dict = (WampDict)obj;
            packer.writeMapBegin(dict.size());
            for(String key : dict.keySet()) {
                packer.write(key);
                write(packer, dict.get(key));
            }
            packer.writeMapEnd();
        } else if(obj instanceof WampList) {
            WampList arr = (WampList)obj;
            packer.writeArrayBegin(arr.size());
            for(int i = 0; i < arr.size(); i++) {
                write(packer, arr.get(i));
            }
            packer.writeArrayEnd();
        }
    }    
    
    @Override
    public WampObject deserialize(Object obj) throws Exception 
    {
        byte[] bytes = (byte[])obj;
        MessagePack msgpack = new MessagePack();
        Unpacker unpacker = msgpack.createUnpacker(new ByteArrayInputStream(bytes));
        unpacker.resetReadByteCount();
        Value val = unpacker.readValue();
        return (WampObject)castToWampObject(val);
    }
    
    public Object castToWampObject(Object obj) 
    {
        Object retval = null;
        if(obj instanceof NilValue) {
            retval = null;
        } else if(obj instanceof BooleanValue) {
            retval = new Boolean(((BooleanValue)obj).getBoolean());
        } else if(obj instanceof IntegerValue) {
            retval = new Long(((IntegerValue)obj).getLong());
        } else if(obj instanceof FloatValue) {
            retval = new Double(((FloatValue)obj).getDouble());
        } else if(obj instanceof String) {
            retval = (String)obj;
        } else if(obj instanceof RawValue) {
            retval = ((RawValue)obj).getString();
        } else if(obj instanceof ArrayValue) {
            retval = createWampList((ArrayValue)obj);
        } else if(obj instanceof MapValue) {
            retval = createWampDict((MapValue)obj);
        } else {
            retval = super.castToWampObject(obj);
        }
        return retval;
    }    
    
    private WampList createWampList(ArrayValue arr) 
    {
        WampList list = new WampList();
        for(int i = 0; i < arr.size(); i++) {
            list.add(castToWampObject(arr.get(i)));
        }
        return list;
    }
    
    private WampDict createWampDict(MapValue node)
    {
        WampDict dict = new WampDict();
        Iterator<Value> iter = node.keySet().iterator();
        while(iter.hasNext()) {
            Value key = iter.next();
            dict.put(key.toString(), castToWampObject(node.get(key)));
        }
        return dict;
    }          
}
