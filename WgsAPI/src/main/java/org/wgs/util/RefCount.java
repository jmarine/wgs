package org.wgs.util;

import java.util.concurrent.atomic.AtomicInteger;


public class RefCount<T>
{
    private T obj;
    private AtomicInteger counter;
    
    public RefCount(T obj, int initialRefCount)
    {
        this.obj = obj;
        this.counter = new AtomicInteger(initialRefCount);
    }
    
    public int refCount(int delta)
    {
        return counter.addAndGet(delta);
    }
    
    public T getObject()
    {
        return obj;
    }
    
}