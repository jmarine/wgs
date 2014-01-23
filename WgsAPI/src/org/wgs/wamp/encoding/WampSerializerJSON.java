package org.wgs.wamp.encoding;

import org.wgs.wamp.types.WampDict;
import org.wgs.wamp.types.WampObject;
import org.wgs.wamp.types.WampList;
import java.util.Iterator;
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
    public WampObject deserialize(Object obj) throws Exception 
    {
        ObjectMapper mapper = new ObjectMapper();
        return (WampObject)castToWampObject(mapper.readTree((String)obj));
    }
    
    
    private JsonNode convertToJsonNode(ObjectMapper mapper, Object obj) throws Exception
    {
        JsonNode retval = null;      
        if(obj != null) {
            if(obj instanceof String) {
                    retval = new org.codehaus.jackson.node.TextNode((String)obj);
            } else if(obj instanceof Boolean) {
                    retval = new org.codehaus.jackson.node.IntNode(((Boolean)obj).booleanValue()? 1 : 0);
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
            retval = ((TextNode)obj).asText();
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
