package org.wgs.wamp;

import java.util.HashMap;
import java.util.Set;


public class WampDict extends WampObject
{
    public WampDict()
    {
        setObject(new HashMap<String,Object>(), Type.dict);
    }
    
    
    private HashMap<String,Object> getHashMap()
    {
        return (HashMap<String,Object>)getObject();
    }
    
    
    public void put(String key, Object obj)
    {
        getHashMap().put(key, castToWampObject(obj));
    }
    
    public void putAll(WampDict obj)
    {
        for(String key : obj.keySet()) {
            getHashMap().put(key, obj.get(key));
        }
    }
    
    
    public WampObject get(String key)
    {
        return (WampObject)getHashMap().get(key);
    }
    
    public boolean has(String key)
    {
        return getHashMap().containsKey(key);
    }
    
    public Set<String> keySet()
    {
        return getHashMap().keySet();
    }
    
}
