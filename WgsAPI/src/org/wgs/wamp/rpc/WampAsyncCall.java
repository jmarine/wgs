package org.wgs.wamp.rpc;

import java.util.concurrent.Callable;
import org.wgs.wamp.type.WampDict;


public abstract class WampAsyncCall implements Callable<Void>
{
    private WampAsyncCallback callback;

    public WampAsyncCall(WampAsyncCallback callback)
    {
        this.callback = callback;
    }
    
    public WampAsyncCallback setAsyncCallback(WampAsyncCallback callback)
    {
        this.callback = callback;
        return this.callback;
    }
        
    public WampAsyncCallback getAsyncCallback()
    {
        return callback;
    }
    
    public boolean hasAsyncCallback()
    {
        return callback != null;
    }    
    

    public abstract void cancel(WampDict cancelOptions);
}

