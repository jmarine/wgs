package org.wgs.wamp.encoding;

import java.io.StringReader;
import java.util.Iterator;

import javax.json.*;
import org.wgs.util.Base64;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampObject;


public class WampSerializerJSON extends WampObject implements WampSerializer
{
    @Override
    public Object serialize(WampObject obj) throws Exception {
        return convertWampObjectToJsonValue(obj).toString();
    }
    
    
    private JsonValue convertWampObjectToJsonValue(WampObject obj) throws Exception
    {
        JsonValue retval = null;      
        if(obj != null) {
            if(obj instanceof WampDict) {
                WampDict dict = (WampDict)obj;
                retval = convertWampDictToJsonValue(dict);

            } else if(obj instanceof WampList) {
                
                WampList arr = (WampList)obj;
                retval = convertWampListToJsonValue(arr);
            }
        }
        return retval;
    }
    
    private JsonValue convertWampDictToJsonValue(WampDict dict) throws Exception
    {
        JsonValue retval = null;      
        if(dict != null) {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            for(String key : dict.keySet()) {
                Object val = dict.get(key);
                if(val == null) {
                    builder.addNull(key);
                } else if(val instanceof String) {
                    builder.add(key, (String)val);
                } else if(val instanceof byte[]) {
                    String str = Character.toString((char)0) + Base64.encodeByteArrayToBase64((byte[])val);
                    builder.add(key, str);
                } else if(val instanceof Boolean) {
                    builder.add(key, (Boolean)val);
                } else if(val instanceof Long) {
                    builder.add(key, (Long)val);
                } else if(val instanceof Float) {
                    builder.add(key, ((Float)val).doubleValue() );
                } else if(val instanceof Double) {
                    builder.add(key, (Double)val);
                } else if(val instanceof WampObject) {                    
                    builder.add(key, convertWampObjectToJsonValue((WampObject)val));
                }
            }
            retval = builder.build();
        }
        
        return retval;
    }
    
    private JsonValue convertWampListToJsonValue(WampList arr) throws Exception
    {
        JsonValue retval = null;      
        if(arr != null) {
            JsonArrayBuilder builder = Json.createArrayBuilder();                

            for(int i = 0; i < arr.size(); i++) {
                Object val = arr.get(i);
                if(val == null) {
                    builder.addNull();
                } else if(val instanceof String) {
                    builder.add((String)val);
                } else if(val instanceof byte[]) {
                    String str = Character.toString((char)0) + Base64.encodeByteArrayToBase64((byte[])val);
                    builder.add(str);
                } else if(val instanceof Boolean) {
                    builder.add((Boolean)val);
                } else if(val instanceof Long) {
                    builder.add((Long)val);
                } else if(val instanceof Float) {
                    builder.add(((Float)val).doubleValue() );
                } else if(val instanceof Double) {
                    builder.add((Double)val);
                } else if(val instanceof WampObject) {                    
                    builder.add(convertWampObjectToJsonValue((WampObject)val));
                }
            }
            
            retval = builder.build();
        }
        return retval;
    }    

    
    
    @Override
    public WampObject deserialize(Object obj, int offset, int len) throws Exception 
    {
        String str = (String)obj;
        try(JsonReader jsonReader = Json.createReader(new StringReader(str.substring(offset, offset+len)))) {
            JsonStructure jsonStructure = jsonReader.read();     
            return (WampObject)castToWampObject(jsonStructure);
        }        
    }
    
    
    public Object castToWampObject(Object obj) 
    {
        Object retval = null;
        if(obj != null) {
            if(obj.equals(javax.json.JsonValue.NULL)) {
                retval = null;
            } else if(obj.equals(javax.json.JsonValue.TRUE)) {
                return Boolean.TRUE;
            } else if(obj.equals(javax.json.JsonValue.FALSE)) {
                return Boolean.FALSE;
            } else if(obj instanceof JsonNumber) {
                JsonNumber num = (JsonNumber)obj;
                if(num.isIntegral()) {
                    retval = new Long(num.longValue());
                } else {
                    retval = new Double(num.doubleValue());
                }
            } else if(obj instanceof JsonString) {
                String str = ((JsonString)obj).getString();
                if(str.length() == 0 || str.charAt(0) != 0) {
                    retval = str;
                } else {
                    try { retval = Base64.decodeBase64ToByteArray(str.substring(1)); }
                    catch(Exception ex) { retval = str; } 
                }
            } else if(obj instanceof JsonArray) {
                retval = createWampList((JsonArray)obj);
            } else if(obj instanceof JsonObject) {
                retval = createWampDict((JsonObject)obj);
            } else {
                retval = super.castToWampObject(obj);
            }
        }
        return retval;
    }
    
    private WampList createWampList(JsonArray arr) 
    {
        WampList list = new WampList();
        for(int i = 0; i < arr.size(); i++) {
            list.add(castToWampObject(arr.get(i)));
        }
        return list;
    }
    
    private WampDict createWampDict(JsonObject node)
    {
        WampDict dict = new WampDict();
        Iterator<String> iter = node.keySet().iterator();
        while(iter.hasNext()) {
            String key = iter.next();
            dict.put(key, castToWampObject(node.get(key)));
        }
        return dict;
    }            
    
}
