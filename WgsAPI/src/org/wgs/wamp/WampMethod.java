package org.wgs.wamp;


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
    
    public abstract Object invoke(WampCallController task, WampSocket clientSocket, WampList args, WampDict argsKw, WampCallOptions options)
        throws Exception;
    
}
