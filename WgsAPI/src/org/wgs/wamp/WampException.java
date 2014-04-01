package org.wgs.wamp;

import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


public class WampException extends Exception
{
    public static final String ERROR_PREFIX   = "wamp.error";
    public static final String NOT_AUTHORIZED = "wamp.error.not_authorized";
    
    private String   errorURI;
    private WampDict details;
    private WampList args;
    private WampDict argsKw;

    
    public WampException(WampDict errorDetails, String errorURI, WampList args, WampDict argsKw) 
    {
        super(errorURI);
        this.errorURI = errorURI;
        this.details = errorDetails;
        this.args = args;
        this.argsKw = argsKw;
    }
    
    
    public String getErrorURI()
    {
        return errorURI;
    }
    
    
    public WampDict getDetails() 
    {
        return details;
    }

    
    public WampList getArgs() {
        return args;
    }

    public WampDict getArgsKw() {
        return argsKw;
    }
    
}
