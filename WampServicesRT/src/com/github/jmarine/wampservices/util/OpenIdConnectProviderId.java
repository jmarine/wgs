package com.github.jmarine.wampservices.util;


import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Id;


@Embeddable
public class OpenIdConnectProviderId implements java.io.Serializable
{
    @Column(name="provider_domain")
    private String domain;

    @Column(name="redirect_url")
    private String redirectUri = "";    
    
    public OpenIdConnectProviderId() { }
    

    public OpenIdConnectProviderId(String domain, String redirectUri) 
    {
        this.domain = domain;
        this.redirectUri = redirectUri;
    }
    

    
    /**
     * @return the domain
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the domain to set
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }
    
    /**
     * @return the redirectUri
     */
    public String getRedirectUri() {
        return redirectUri;
    }

    /**
     * @param redirectUri the redirectUri to set
     */
    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }    
    

    @Override
    public boolean equals(Object o) { 
        if( (o != null) && (o instanceof OpenIdConnectProviderId) ) {
            OpenIdConnectProviderId pk = (OpenIdConnectProviderId)o;
            return domain.equals(pk.getDomain()) && redirectUri.equals(pk.getRedirectUri());
        } else {
            return false;
        }
    }
    
}
