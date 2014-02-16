package org.wgs.entity;

import javax.persistence.Column;
import javax.persistence.Embeddable;


@Embeddable
public class UserId implements java.io.Serializable
{
    @Column(name="uid", nullable = false)    
    private String uid;
    
    @Column(name="oidc_provider", nullable = false)    
    private String providerDomain;    
    
    public UserId() { }
    
    public UserId(String fqUser) 
    {
        int pos = fqUser.indexOf("@");
        if(pos == -1) {
            this.uid = fqUser;
            this.providerDomain = "";
        } else {
            this.uid = fqUser.substring(0, pos);
            this.providerDomain = fqUser.substring(pos+1);
        }
    }
    
    public UserId(String providerDomain, String uid) 
    {
        if(providerDomain == null) providerDomain = "";
        this.providerDomain = providerDomain;
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
     * @return the providerDomain
     */
    public String getProviderDomain() {
        return providerDomain;
    }

    /**
     * @param providerDomain the providerDomain to set
     */
    public void setProviderDomain(String providerDomain) {
        this.providerDomain = providerDomain;
    }
    

    @Override
    public boolean equals(Object o) { 
        if( (o != null) && (o instanceof UserId) ) {
            UserId pk = (UserId)o;
            return uid.equals(pk.getUid()) && providerDomain.equals(pk.getProviderDomain());
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
        if(providerDomain == null || providerDomain.length() == 0) {
            return uid;
        } else {
            return uid + "@" + providerDomain;
        }
    }
    
}
