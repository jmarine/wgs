package org.wgs.wamp;

import java.util.HashMap;
import java.util.Set;


public class WampDict extends WampObject
{
    public WampDict()
    {
        setObject(new HashMap<String,Object>(), Type.dict);
    }
    

    public boolean has(String key)
    {
        return getHashMap().containsKey(key);
    }

    
    public WampObject get(String key)
    {
        return (WampObject)getHashMap().get(key);
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
    
    public void remove(String key)
    {
        getHashMap().remove(key);
    }
    

    public Set<String> keySet()
    {
        return getHashMap().keySet();
    }

    
    public int size()
    {
        return getHashMap().size();
    }

    
    private HashMap<String,Object> getHashMap()
    {
        return (HashMap<String,Object>)getObject();
    }
    
}
