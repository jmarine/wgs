package org.wgs.wamp.rpc;


public interface WampAsyncCallback<T> 
{
    void resolve(T obj);
    
    void progress(T obj);
    
    void reject(Throwable th);
}
