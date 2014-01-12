package org.wgs.wamp;


public interface Promise 
{
    void resolve(Object ... results);
    
    void progress(Object ... progress);
    
    void reject(Object ... errors);
}
