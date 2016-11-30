package org.wgs.security;


public class OpenIdConnectClientPK implements java.io.Serializable
{
    private static final long serialVersionUID = 0L;

    private String provider;
    private String clientName = "";    
    
    public OpenIdConnectClientPK() { }
    
    public OpenIdConnectClientPK(String provider, String clientName) 
    {
        this.provider = provider;
        this.clientName = clientName;
    }


    @Override
    public boolean equals(Object o) { 
        if( (o != null) && (o instanceof OpenIdConnectClientPK) ) {
            OpenIdConnectClientPK pk = (OpenIdConnectClientPK)o;
            return provider.equals(pk.provider) && clientName.equals(pk.clientName);
        } else {
            return false;
        }
    }
    
    
    @Override
    public int hashCode() {
        return provider.hashCode() + clientName.hashCode();
    }
    
}
