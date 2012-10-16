package com.github.jmarine.wampservices.wsg;


public class UserId implements java.io.Serializable
{
    private String uid;
    private String openIdProviderUrl;    
    
    public UserId() { }
    
    public UserId(String uid, String openIdProviderUrl) 
    {
        this.uid = uid;
        this.openIdProviderUrl = openIdProviderUrl;
    }
    
    /**
     * @return the uid
     */
    public String getUid() {
        return uid;
    }

    /**
     * @param uid the uid to set
     */
    public void setUid(String uid) {
        this.uid = uid;
    }
    
    
    /**
     * @return the domain
     */
    public String getOpenIdProviderUrl() {
        return openIdProviderUrl;
    }

    /**
     * @param domain the domain to set
     */
    public void setOpenIdProviderUrl(String openIdProviderUrl) {
        this.openIdProviderUrl = openIdProviderUrl;
    }
    

    public boolean equals(Object o) { 
        if( (o != null) && (o instanceof UserId) ) {
            UserId pk = (UserId)o;
            return uid.equals(pk.getUid()) && openIdProviderUrl.equals(pk.getOpenIdProviderUrl());
        } else {
            return false;
        }
    }

    public int hashCode() { 
        return (openIdProviderUrl + "#" + uid).hashCode(); 
    }
    
}
