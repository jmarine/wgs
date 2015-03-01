package org.wgs.wamp.encoding;

import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampObject;
import org.wgs.wamp.type.WampList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Map;
import org.msgpack.core.MessageFormat;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ArrayValue;
import org.msgpack.value.BooleanValue;
import org.msgpack.value.FloatValue;
import org.msgpack.value.IntegerValue;
import org.msgpack.value.MapValue;
import org.msgpack.value.NilValue;
import org.msgpack.value.RawValue;
import org.msgpack.value.Value;
import org.msgpack.value.holder.ValueHolder;



public class WampSerializerMsgPack extends WampObject implements WampSerializer
{
    @Override
    public Object serialize(WampObject obj) throws Exception 
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MessagePack msgpack = new MessagePack();
        MessagePacker packer = msgpack.newPacker(baos);
        write(packer, obj);
        packer.close();
        byte[] bytes = baos.toByteArray();
        return bytes;
    }

    private void write(MessagePacker packer, Object obj) throws Exception
    {
        if(obj == null) {
            packer.packNil();
        } else if(obj instanceof String) {
            packer.packString((String)obj);
        } else if(obj instanceof Boolean) {
            packer.packBoolean((boolean)obj);
        } else if(obj instanceof Long) {
            packer.packLong((long)obj);
        } else if(obj instanceof Double) {
            packer.packDouble((double)obj);
        } else if(obj instanceof WampDict) {                    
            WampDict dict = (WampDict)obj;
            packer.packMapHeader(dict.size());
            for(String key : dict.keySet()) {
                packer.packString(key);
                write(packer, dict.get(key));
            }
            //packer.packMapEnd();
        } else if(obj instanceof WampList) {
            WampList arr = (WampList)obj;
            packer.packArrayHeader(arr.size());
            for(int i = 0; i < arr.size(); i++) {
                write(packer, arr.get(i));
            }
            //packer.packArrayEnd();
        }
    }    
    
    
    @Override
    public WampObject deserialize(Object obj, int offset, int len) throws Exception 
    {
        MessagePack msgpack = new MessagePack();
        MessageUnpacker unpacker = msgpack.newUnpacker(new ByteArrayInputStream((byte[])obj, offset, len));
        //unpacker.resetReadByteCount();
        ValueHolder vh = new ValueHolder();
        MessageFormat fmt = unpacker.unpackValue(vh);
        Value val = vh.get();
        return (WampObject)castToWampObject(val);
    }
    
    public Object castToWampObject(Object obj) 
    {
        Object retval = null;
        if(obj instanceof NilValue) {
            retval = null;
        } else if(obj instanceof BooleanValue) {
            retval = new Boolean(((BooleanValue)obj).toBoolean());
        } else if(obj instanceof IntegerValue) {
            retval = new Long(((IntegerValue)obj).toLong());
        } else if(obj instanceof FloatValue) {
            retval = new Double(((FloatValue)obj).toDouble());
        } else if(obj instanceof String) {
            retval = (String)obj;
        } else if(obj instanceof RawValue) {
            retval = ((RawValue)obj).toString();
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
        Map<Value,Value> map = node.toMap();
        Iterator<Value> iter = map.keySet().iterator();
        while(iter.hasNext()) {
            Value key = iter.next();
            dict.put(castToWampObject(key).toString(), castToWampObject(map.get(key)));
        }
        return dict;
    }          
    
}
