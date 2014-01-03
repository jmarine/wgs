package org.wgs.wamp;

import java.math.BigDecimal;
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
        return castToWampObject(mapper.readTree((String)obj));
    }
    
    
    private JsonNode convertToJsonNode(ObjectMapper mapper, WampObject obj) throws Exception
    {
        JsonNode retval = null;      
        if(obj != null) {
            switch(obj.getType()) {
                case string:
                    retval = new org.codehaus.jackson.node.TextNode(obj.asText());
                    break;       
                case bool:                    
                    retval = new org.codehaus.jackson.node.IntNode(obj.asBoolean()? 1:0);
                    break;                    
                case integer:
                    retval = new org.codehaus.jackson.node.LongNode(obj.asLong());
                    break;
                case real:
                    retval = new org.codehaus.jackson.node.DoubleNode(obj.asDouble());
                    break;
                case dict:
                    WampDict dict = (WampDict)obj;
                    ObjectNode objNode = mapper.createObjectNode();
                    for(String key : dict.keySet()) {
                        objNode.put(key, convertToJsonNode(mapper, dict.get(key)));
                    }
                    retval = objNode;
                    break;
                case list:
                    WampList arr = (WampList)obj;
                    ArrayNode arrNode = mapper.createArrayNode();
                    for(int i = 0; i < arr.size(); i++) {
                        arrNode.add(convertToJsonNode(mapper, arr.get(i)));
                    }
                    retval = arrNode;
                    break;                
            }
        }
        return retval;
    }
    

    
    public WampObject castToWampObject(Object obj) {
        WampObject retval = null;
        if(obj instanceof NullNode) {
            retval = new WampObject();
        } else if(obj instanceof BooleanNode) {
            retval = new WampObject();
            retval.setObject(((BooleanNode)obj).asBoolean(), Type.bool);
        } else if(obj instanceof IntNode) {
            retval = new WampObject();
            retval.setObject(((IntNode)obj).asLong(), Type.integer);
        } else if(obj instanceof LongNode) {
            retval = new WampObject();
            retval.setObject(((LongNode)obj).asLong(), Type.integer);
        } else if(obj instanceof DoubleNode) {
            retval = new WampObject();
            retval.setObject(((DoubleNode)obj).asDouble(), Type.real);
        } else if(obj instanceof TextNode) {
            retval = new WampObject();
            retval.setObject(((TextNode)obj).asText(), Type.string);
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
