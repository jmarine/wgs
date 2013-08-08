package org.wgs.entity;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Id;


@Embeddable
public class UserId implements java.io.Serializable
{
    @Column(name="uid", nullable = false)    
    private String uid;
    
    @Column(name="oidc_provider", nullable = false)    
    private String openIdConnectProviderDomain;    
    
    public UserId() { }
    
    public UserId(String fqUser) 
    {
        int pos = fqUser.indexOf("@");
        if(pos == -1) {
            this.uid = fqUser;
            this.openIdConnectProviderDomain = "";
        } else {
            this.uid = fqUser.substring(0, pos);
            this.openIdConnectProviderDomain = fqUser.substring(pos+1);
        }
    }
    
    public UserId(String openIdConnectProviderDomain, String uid) 
    {
        if(openIdConnectProviderDomain == null) openIdConnectProviderDomain = "";
        this.openIdConnectProviderDomain = openIdConnectProviderDomain;
        this.uid = uid;
    }
    
    /**
     * @return the user
     */
    public String getUid() {
        return uid;
    }

    /**
     * @param uid the user to set
     */
    public void setUid(String uid) {
        this.uid = uid;
    }
    
    
    /**
     * @return the openIdConnectProviderDomain
     */
    public String getOpenIdConnectProviderDomain() {
        return openIdConnectProviderDomain;
    }

    /**
     * @param openIdConnectProviderDomain the openIdConnectProviderDomain to set
     */
    public void setOpenIdConnectProviderDomain(String openIdConnectProviderDomain) {
        this.openIdConnectProviderDomain = openIdConnectProviderDomain;
    }
    

    @Override
    public boolean equals(Object o) { 
        if( (o != null) && (o instanceof UserId) ) {
            UserId pk = (UserId)o;
            return uid.equals(pk.getUid()) && openIdConnectProviderDomain.equals(pk.getOpenIdConnectProviderDomain());
        } else {
            return false;
        }
    }
    

    @Override
    public int hashCode() { 
        return toString().hashCode(); 
    }
    
    
    @Override
    public String toString()
    {
        if(openIdConnectProviderDomain == null || openIdConnectProviderDomain.length() == 0) {
            return uid;
        } else {
            return uid + "@" + openIdConnectProviderDomain;
        }
    }
    
}
