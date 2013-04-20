package org.wampservices;


public class WampException extends Exception
{
    public static final String WAMP_GENERIC_ERROR_URI = "http://wamp.ws/err";
    
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
