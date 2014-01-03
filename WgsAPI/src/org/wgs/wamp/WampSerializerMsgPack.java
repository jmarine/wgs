package org.wgs.wamp;

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
import org.msgpack.type.Value;
import org.msgpack.unpacker.Unpacker;


public class WampSerializerMsgPack extends WampObject implements WampSerializer
{
    @Override
    public Object serialize(WampObject obj) throws Exception {
        MessagePack msgpack = new MessagePack();
        BufferPacker packer = msgpack.createBufferPacker();
        write(packer, obj);
        byte[] bytes = packer.toByteArray();
        return bytes;
    }

    private void write(BufferPacker packer, WampObject obj) throws Exception
    {
        if(obj == null) {
            packer.writeNil();
        } else {
            switch(obj.getType()) {
                case string:
                    packer.write((String)obj.asText());
                    break;
                case bool:
                    packer.write((boolean)obj.asBoolean());
                    break;                
                case integer:
                    packer.write((long)obj.asLong());
                    break;
                case real:
                    packer.write((double)obj.asDouble());
                    break;
                case dict:
                    WampDict dict = (WampDict)obj;
                    packer.writeMapBegin(dict.size());
                    for(String key : dict.keySet()) {
                        packer.write(key);
                        write(packer, dict.get(key));
                    }
                    packer.writeMapEnd();
                    break;
                case list:
                    WampList arr = (WampList)obj;
                    packer.writeArrayBegin(arr.size());
                    for(int i = 0; i < arr.size(); i++) {
                        write(packer, arr.get(i));
                    }
                    packer.writeArrayEnd();
                    break;                
            }
        }
        
    }    
    
    @Override
    public WampObject deserialize(Object obj) throws Exception {
        byte[] bytes = (byte[])obj;
        MessagePack msgpack = new MessagePack();
        Unpacker unpacker = msgpack.createUnpacker(new ByteArrayInputStream(bytes));
        unpacker.resetReadByteCount();
        Value val = unpacker.readValue();
        return castToWampObject(val);
    }
    
    public WampObject castToWampObject(Object obj) {
        WampObject retval = null;
        if(obj instanceof NilValue) {
            retval = new WampObject();
        } else if(obj instanceof BooleanValue) {
            retval = new WampObject();
            retval.setObject(((BooleanValue)obj).getBoolean(), Type.bool);
        } else if(obj instanceof IntegerValue) {
            retval = new WampObject();
            retval.setObject(((IntegerValue)obj).getInt(), Type.integer);
        } else if(obj instanceof FloatValue) {
            retval = new WampObject();
            retval.setObject(((FloatValue)obj).getDouble(), Type.real);
        } else if(obj instanceof String) {
            retval = new WampObject();
            retval.setObject((String)obj, Type.string);
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
