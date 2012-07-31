/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.jmarine.wampservices;

/**
 *
 * @author jordi
 */
public class WampException extends Exception
{
    public static final String WAMP_GENERIC_ERROR_URI = WampApplication.WAMP_BASE_URL + "#error";
    
    private String errorURI;
    

    public WampException(String errorURI, String errorDesc) {
        super(errorDesc);
        this.errorURI = errorURI;
    }
    
    public String getErrorURI()
    {
        return errorURI;
    }
    
    public String getErrorDesc()
    {
        return getMessage();
    }
    
}
