package org.wampservices;

import java.util.concurrent.Callable;


public abstract class WampCall<T> implements Callable<T>
{

    public abstract void cancel(String cancelMode);
    
}
