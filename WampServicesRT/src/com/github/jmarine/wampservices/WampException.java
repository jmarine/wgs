package com.github.jmarine.wampservices;


public class WampException extends Exception
{
    public static final String WAMP_GENERIC_ERROR_URI = WampApplication.WAMP_BASE_URL + "#error";
    
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
