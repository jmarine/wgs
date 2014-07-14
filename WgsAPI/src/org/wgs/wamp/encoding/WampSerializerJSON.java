package org.wgs.wamp.encoding;

import java.util.Iterator;

import org.wgs.util.Base64;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampObject;
import org.wgs.wamp.type.WampList;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.BooleanNode;
import org.codehaus.jackson.node.DoubleNode;
import org.codehaus.jackson.node.IntNode;
import org.codehaus.jackson.node.LongNode;
import org.codehaus.jackson.node.NullNode;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.node.TextNode;


public class WampSerializerJSON extends WampObject implements WampSerializer
{
    @Override
    public Object serialize(WampObject obj) throws Exception {
        ObjectMapper mapper = new ObjectMapper();               
        return convertToJsonNode(mapper, obj).toString();
    }
    
    @Override
    public WampObject deserialize(Object obj, int offset, int len) throws Exception 
    {
        String str = (String)obj;
        ObjectMapper mapper = new ObjectMapper();
        if(offset == 0 && len == str.length()) {
            return (WampObject)castToWampObject(mapper.readTree(str));
        } else {
            return (WampObject)castToWampObject(mapper.readTree(str.substring(offset, offset+len)));
        }
    }
    
    
    private JsonNode convertToJsonNode(ObjectMapper mapper, Object obj) throws Exception
    {
        JsonNode retval = null;      
        if(obj != null) {
            if(obj instanceof String) {
                retval = new org.codehaus.jackson.node.TextNode((String)obj);
            } else if(obj instanceof byte[]) {
                String str = Character.toString((char)0) + Base64.encodeByteArrayToBase64((byte[])obj);
                retval = new org.codehaus.jackson.node.TextNode(str);
            } else if(obj instanceof Boolean) {
                if(((Boolean)obj).booleanValue()) {
                    retval = org.codehaus.jackson.node.BooleanNode.TRUE;
                } else {
                    retval = org.codehaus.jackson.node.BooleanNode.FALSE;
                }
            } else if(obj instanceof Long) {
                retval = new org.codehaus.jackson.node.LongNode((Long)obj);
            } else if(obj instanceof Double) {
                retval = new org.codehaus.jackson.node.DoubleNode((Double)obj);
            } else if(obj instanceof WampDict) {
                WampDict dict = (WampDict)obj;
                ObjectNode objNode = mapper.createObjectNode();
                for(String key : dict.keySet()) {
                    objNode.put(key, convertToJsonNode(mapper, dict.get(key)));
                }
                retval = objNode;
            } else if(obj instanceof WampList) {
                WampList arr = (WampList)obj;
                ArrayNode arrNode = mapper.createArrayNode();
                for(int i = 0; i < arr.size(); i++) {
                    arrNode.add(convertToJsonNode(mapper, arr.get(i)));
                }
                retval = arrNode;
            }
        }
        return retval;
    }
    

    
    public Object castToWampObject(Object obj) {
        Object retval = null;
        if(obj instanceof NullNode) {
            retval = null;
        } else if(obj instanceof BooleanNode) {
            retval = new Boolean(((BooleanNode)obj).asBoolean());
        } else if(obj instanceof IntNode) {
            retval = new Long(((IntNode)obj).asLong());
        } else if(obj instanceof LongNode) {
            retval = new Long(((LongNode)obj).asLong());
        } else if(obj instanceof DoubleNode) {
            retval = new Double(((DoubleNode)obj).asDouble());
        } else if(obj instanceof TextNode) {
            String str = ((TextNode)obj).asText();
            if(str.length() == 0 || str.charAt(0) != 0) {
                retval = str;
            } else {
                try { retval = Base64.decodeBase64ToByteArray(str.substring(1)); }
                catch(Exception ex) { retval = str; } 
            }
        } else if(obj instanceof ArrayNode) {
            retval = createWampList((ArrayNode)obj);
        } else if(obj instanceof ObjectNode) {
            retval = createWampDict((ObjectNode)obj);
        } else {
            retval = super.castToWampObject(obj);
        }
        return retval;
    }
    
    private WampList createWampList(ArrayNode arr) 
    {
        WampList list = new WampList();
        for(int i = 0; i < arr.size(); i++) {
            list.add(castToWampObject(arr.get(i)));
        }
        return list;
    }
    
    private WampDict createWampDict(ObjectNode node)
    {
        WampDict dict = new WampDict();
        Iterator<String> iter = node.getFieldNames();
        while(iter.hasNext()) {
            String key = iter.next();
            dict.put(key, castToWampObject(node.get(key)));
        }
        return dict;
    }            
    
}
