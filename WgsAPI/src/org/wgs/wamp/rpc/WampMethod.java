package org.wgs.wamp.rpc;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.WampSocket;



public abstract class WampMethod 
{
    private String uri;
    
    public WampMethod(String uri) {
        this.uri = uri;
    }
    
    public String getProcedureURI()
    {
        return uri;
    }
    
    public abstract Object invoke(WampCallController task, WampSocket clientSocket, WampList args, WampDict argsKw, WampCallOptions options, WampAsyncCallback callback)
        throws Exception;
    
}
