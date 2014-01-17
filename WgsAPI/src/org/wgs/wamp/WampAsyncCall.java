package org.wgs.wamp;

import java.util.concurrent.Callable;


public abstract class WampAsyncCall implements Callable<Void>
{
    private WampRpcCallback callback;

    public WampAsyncCall(WampRpcCallback callback)
    {
        this.callback = callback;
    }
    
    public WampRpcCallback setRpcCallback(WampRpcCallback callback)
    {
        this.callback = callback;
        return this.callback;
    }
        
    public WampRpcCallback getRpcCallback()
    {
        return callback;
    }
    
    public boolean hasCallback()
    {
        return callback != null;
    }    
    

    public abstract void cancel(WampDict cancelOptions);
}

