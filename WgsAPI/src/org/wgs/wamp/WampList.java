package org.wgs.wamp;

import java.util.ArrayList;
import java.util.List;


public class WampList extends WampObject
{
    private List<Object> array;
    
    public WampList()
    {
        array = new ArrayList<Object>();
    }
    
    private WampList(List<Object> list) {
        list = list;
    }
    
    
    public Object get(int index)
    {
        return array.get(index);
    }
    
    public Long getLong(int index)
    {
        return (Long)get(index);
    }
    
    public Double getDouble(int index)
    {
        return (Double)get(index);
    }    

    public String getText(int index)
    {
        return (String)get(index);
    }        
    
    public Boolean getBoolean(int index)
    {
        Object v = get(index);
        if(v == null) return Boolean.FALSE;
        else if(v instanceof Boolean) return (Boolean)v;
        else if(v instanceof Long) return ((Long)v).longValue() != 0L;
        else return Boolean.TRUE;
    }    
    
    public void set(int index, Object obj)
    {
        array.set(index, castToWampObject(obj));
    }
    
    public void add(Object obj)
    {
        array.add(castToWampObject(obj));
    }
    
    public WampList subList(int fromIndex, int toIndex) 
    {
        return new WampList(array.subList(fromIndex, toIndex));
    }

    public int size()
    {
        return array.size();
    }
    
    
}
