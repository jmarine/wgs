package org.wgs.wamp;


public interface WampSerializer 
{
    Object serialize(WampObject obj) throws Exception;
    
    WampObject deserialize(Object obj) throws Exception;
        
}
