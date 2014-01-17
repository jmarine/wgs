package org.wgs.wamp;

import java.util.concurrent.Callable;


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

