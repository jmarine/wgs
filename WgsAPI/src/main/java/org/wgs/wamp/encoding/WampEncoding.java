package org.wgs.wamp.encoding;

import org.wgs.wamp.WampException;


public enum WampEncoding 
{ 
    JSON, 
    BatchedJSON, 
    MsgPack,
    BatchedMsgPack;
    
    public WampSerializer getSerializer() throws WampException
    {
        switch(this) {
            case JSON:
                return new WampSerializerJSON();
            case BatchedJSON:
                return new WampSerializerBatchedJSON();
            case MsgPack:
                return new WampSerializerMsgPack();                
            case BatchedMsgPack:
                return new WampSerializerBatchedMsgPack();
            default:
                throw new WampException(null, "wamp.error.unsupported_encoding", null, null);
        }
    }
    
};
