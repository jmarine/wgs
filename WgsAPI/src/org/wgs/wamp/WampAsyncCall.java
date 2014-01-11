package org.wgs.wamp;

import java.util.concurrent.Callable;


public abstract class WampAsyncCall
{
    public abstract void call() throws Exception;
    
    public abstract void cancel(WampDict cancelOptions);
    
}
