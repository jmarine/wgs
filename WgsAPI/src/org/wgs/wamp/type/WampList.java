package org.wgs.wamp.type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class WampList extends WampObject
{
    private List<Object> array;
    
    public WampList()
    {
        this.array = new ArrayList<Object>();
    }
    
    public WampList(Object ... values) 
    {
        this();
        addAll(values);
    }    
    
    private WampList(List<Object> list) 
    {
        this.array = list;
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
    
    public void addAll(Object ... values) 
    {
        if(values != null) {
            for(Object val : values) {
                add(val);
            }
        }
    }    
    
    public WampList subList(int fromIndex, int toIndex) 
    {
        return new WampList(array.subList(fromIndex, toIndex));
    }
    
    public Object remove(int index)
    {
        return array.remove(index);
    }
    
    public boolean contains(Object obj) 
    {
        return array.contains(obj);
    }

    public int size()
    {
        return array.size();
    }
    
    @Override
    public String toString()
    {
        return array.toString();
    }
    
    
}
