package org.wgs.wamp.rpc;


public interface WampAsyncCallback 
{
    void resolve(Object ... results);
    
    void progress(Object ... progress);
    
    void reject(Object ... errors);
}
