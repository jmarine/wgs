package org.wgs.wamp;

import java.util.ArrayList;
import java.util.List;


public class WampList extends WampObject
{
    public WampList()
    {
        setObject(new ArrayList<WampObject>(), Type.list);
    }
    
    private WampList(List<WampObject> list) {
        setObject(list, Type.list);
    }
    
    private List<WampObject> getList()
    {
        return ((List<WampObject>)getObject());
    }
    
    public WampObject get(int index)
    {
        return (WampObject)getList().get(index);
    }
    
    public void set(int index, Object obj)
    {
        getList().set(index, castToWampObject(obj));
    }
    
    public void add(Object obj)
    {
        getList().add(castToWampObject(obj));
    }
    
    public WampList subList(int fromIndex, int toIndex) 
    {
        return new WampList(getList().subList(fromIndex, toIndex));
    }

    public int size()
    {
        return getList().size();
    }
    
    
}
