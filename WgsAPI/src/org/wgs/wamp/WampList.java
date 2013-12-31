package org.wgs.wamp;

import java.util.ArrayList;


public class WampList extends WampObject
{
    public WampList()
    {
        setObject(new ArrayList<WampObject>(), Type.list);
    }
    
    private ArrayList<WampObject> getArrayList()
    {
        return ((ArrayList<WampObject>)getObject());
    }
    
    public WampObject get(int index)
    {
        return (WampObject)getArrayList().get(index);
    }
    
    public void set(int index, Object obj)
    {
        getArrayList().set(index, castToWampObject(obj));
    }
    
    public void add(Object obj)
    {
        getArrayList().add(castToWampObject(obj));
    }
    
    public int size()
    {
        return getArrayList().size();
    }
    
}
