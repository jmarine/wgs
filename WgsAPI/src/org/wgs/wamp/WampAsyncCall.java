package org.wgs.wamp;

import java.util.concurrent.Callable;


public abstract class WampAsyncCall implements Callable<Void>
{
    private Promise promise;

    public WampAsyncCall(Promise promise)
    {
        this.promise = promise;
    }
    
    public WampAsyncCall setPromise(Promise promise)
    {
        this.promise = promise;
        return this;
    }
    
    public Promise getPromise()
    {
        return promise;
    }
    
    public boolean hasPromise()
    {
        return promise != null;
    }    
    

    public abstract void cancel(WampDict cancelOptions);
}

