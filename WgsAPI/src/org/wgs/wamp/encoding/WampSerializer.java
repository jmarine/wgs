package org.wgs.wamp.encoding;

import org.wgs.wamp.type.WampObject;


public interface WampSerializer 
{
    Object serialize(WampObject obj) throws Exception;
    
    WampObject deserialize(Object obj, int offset, int len) throws Exception;
        
}
