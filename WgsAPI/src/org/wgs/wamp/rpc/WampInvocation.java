package org.wgs.wamp.rpc;

import org.jdeferred.Deferred;
import org.wgs.wamp.WampException;
import org.wgs.wamp.WampResult;


public class WampInvocation 
{
    private Long invocationId;
    private WampCallController controller;
    private Deferred<WampResult, WampException, WampResult> asyncCallback;
    
    public WampInvocation(Long invocationId,
                              WampCallController controller,
                              Deferred<WampResult, WampException, WampResult> asyncCallback)
    {
        this.invocationId = invocationId;
        this.controller = controller;
        this.asyncCallback = asyncCallback;
    }

    /**
     * @return the invocationId
     */
    public Long getInvocationId() {
        return invocationId;
    }

    /**
     * @return the controller
     */
    public WampCallController getWampCallController() {
        return controller;
    }

    /**
     * @return the asyncCallback
     */
    public Deferred<WampResult, WampException, WampResult> getAsyncCallback() {
        return asyncCallback;
    }
}
