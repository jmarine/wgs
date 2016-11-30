package org.wgs.wamp;

import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;

public class WampResult 
{
    private Long requestId;
    private WampDict details;
    private WampList args;
    private WampDict argsKw;

    public WampResult(Long requestId) 
    {
        this.requestId = requestId;
    }
    
    
    /**
     * @return the requestId
     */
    public Long getRequestId() {
        return requestId;
    }

    
    public boolean isProgressResult()
    {
        return (details != null && details.has("progress") && details.getBoolean("progress"))
                || (details != null && details.has("receive_progress") && details.getBoolean("receive_progress") );
    }
        

    /**
     * @return the details
     */
    public WampDict getDetails() {
        return details;
    }

    /**
     * @param details the details to set
     */
    public void setDetails(WampDict details) {
        this.details = details;
    }

    /**
     * @return the args
     */
    public WampList getArgs() {
        return args;
    }

    /**
     * @param args the args to set
     */
    public void setArgs(WampList args) {
        this.args = args;
    }

    /**
     * @return the argsKw
     */
    public WampDict getArgsKw() {
        return argsKw;
    }

    /**
     * @param argsKw the argsKw to set
     */
    public void setArgsKw(WampDict argsKw) {
        this.argsKw = argsKw;
    }
    
    
}
