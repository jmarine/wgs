package org.wgs.security;


public class OpenIdConnectClientPK implements java.io.Serializable
{
    private String provider;
    private String redirectUri = "";    
    
    public OpenIdConnectClientPK() { }
    
    public OpenIdConnectClientPK(String provider, String redirectUri) 
    {
        this.provider = provider;
        this.redirectUri = redirectUri;
    }


    @Override
    public boolean equals(Object o) { 
        if( (o != null) && (o instanceof OpenIdConnectClientPK) ) {
            OpenIdConnectClientPK pk = (OpenIdConnectClientPK)o;
            return provider.equals(pk.provider) && redirectUri.equals(pk.redirectUri);
        } else {
            return false;
        }
    }
    
    
    @Override
    public int hashCode() {
        return provider.hashCode() + redirectUri.hashCode();
    }
    
}
