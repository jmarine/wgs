package org.wgs.wamp.encoding;

import org.wgs.wamp.types.WampObject;


public interface WampSerializer 
{
    Object serialize(WampObject obj) throws Exception;
    
    WampObject deserialize(Object obj) throws Exception;
        
}
