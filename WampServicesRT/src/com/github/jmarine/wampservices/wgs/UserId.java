package com.github.jmarine.wampservices.wgs;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Id;


@Embeddable
public class UserId implements java.io.Serializable
{
    @Column(name="uid", nullable = false)    
    private String uid;
    
    @Column(name="oic_provider", nullable = false)    
    private String openIdConnectProviderUrl;    
    
    public UserId() { }
    
    public UserId(String fqUser) 
    {
        int pos = fqUser.indexOf("#");
        if(pos == -1) {
            this.openIdConnectProviderUrl = "";
            this.uid = fqUser;
        } else {
            this.openIdConnectProviderUrl = fqUser.substring(0, pos);
            this.uid = fqUser.substring(pos+1);
        }
    }
    
    public UserId(String openIdConnectProviderUrl, String uid) 
    {
        if(openIdConnectProviderUrl == null) openIdConnectProviderUrl = "";
        this.openIdConnectProviderUrl = openIdConnectProviderUrl;
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
     * @return the openIdConnectProviderUrl
     */
    public String getOpenIdConnectProviderUrl() {
        return openIdConnectProviderUrl;
    }

    /**
     * @param openIdConnectProviderUrl the openIdConnectProviderUrl to set
     */
    public void setOpenIdConnectProviderUrl(String openIdConnectProviderUrl) {
        this.openIdConnectProviderUrl = openIdConnectProviderUrl;
    }
    

    @Override
    public boolean equals(Object o) { 
        if( (o != null) && (o instanceof UserId) ) {
            UserId pk = (UserId)o;
            return uid.equals(pk.getUid()) && openIdConnectProviderUrl.equals(pk.getOpenIdConnectProviderUrl());
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
        if(openIdConnectProviderUrl == null || openIdConnectProviderUrl.length() == 0) {
            return uid;
        } else {
            return openIdConnectProviderUrl + "#" + uid;
        }
    }
    
}
