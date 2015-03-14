package org.wgs.wamp.type;

import java.util.HashMap;
import java.util.Set;


public class WampDict extends WampObject
{
    private HashMap<String,Object> hashmap;
            
    public WampDict()
    {
        hashmap = new HashMap<String,Object>();
    }
    

    public boolean has(String key)
    {
        return hashmap.containsKey(key);
    }

    
    public Object get(String key)
    {
        return hashmap.get(key);
    }

    
    public Long getLong(String key)
    {
        return (Long)get(key);
    }
    
    public Double getDouble(String key)
    {
        return (Double)get(key);
    }    

    public String getText(String key)
    {
        return (String)get(key);
    }        
    
    public Boolean getBoolean(String key)
    {
        Object v = get(key);
        if(v == null) return Boolean.FALSE;
        else if(v instanceof Boolean) return (Boolean)v;
        else if(v instanceof Long) return ((Long)v).longValue() != 0L;
        else return Boolean.TRUE;
    }      
    
    
    public WampDict put(String key, Object obj)
    {
        hashmap.put(key, castToWampObject(obj));
        return this;
    }

    
    public void putAll(WampDict obj)
    {
        if(obj != null) {
            for(String key : obj.keySet()) {
                hashmap.put(key, obj.get(key));
            }
        }
    }
    
    public Object remove(String key)
    {
        return hashmap.remove(key);
    }
    
    public void clear()
    {
        hashmap.clear();
    }

    public Set<String> keySet()
    {
        return hashmap.keySet();
    }

    
    public int size()
    {
        return hashmap.size();
    }
    
    @Override
    public String toString()
    {
        return hashmap.toString();
    }
        
}
