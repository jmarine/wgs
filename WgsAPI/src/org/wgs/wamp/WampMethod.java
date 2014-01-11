package org.wgs.wamp;


public interface WampMethod 
{
    Object invoke(WampCallController task, WampSocket clientSocket, WampList args, WampDict argsKw, WampCallOptions options)
        throws Exception;
    
}
