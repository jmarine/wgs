package org.wgs.wamp;


public interface WampRpcCallback 
{
    void resolve(Object ... results);
    
    void progress(Object ... progress);
    
    void reject(Object ... errors);
}
