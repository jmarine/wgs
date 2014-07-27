package org.wgs.wamp.rpc;
import org.jdeferred.Deferred;
import org.jdeferred.Promise;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;



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
    
    public abstract Promise invoke(WampCallController task, WampSocket clientSocket, WampList args, WampDict argsKw, WampCallOptions options)
        throws Exception;
    
}
