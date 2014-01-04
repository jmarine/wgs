package org.wgs.wamp;


public class WampException extends Exception
{
    public static final String ERROR_PREFIX   = "wamp.error";
    public static final String NOT_AUTHORIZED = "wamp.error.not_authorized";
    
    private String errorURI;
    private Object errorDetails;
    
    
    public WampException(String errorURI, String errorDesc) 
    {
        this(errorURI, errorDesc, null);
    }

    
    public WampException(String errorURI, String errorDesc, Object errorDetails) 
    {
        super(errorDesc);
        this.errorURI = errorURI;
        this.errorDetails = errorDetails;
    }
    
    
    public String getErrorURI()
    {
        return errorURI;
    }
    
    
    public String getErrorDesc()
    {
        return getMessage();
    }
    
    
    public Object getErrorDetails() 
    {
        return errorDetails;
    }
    
}
