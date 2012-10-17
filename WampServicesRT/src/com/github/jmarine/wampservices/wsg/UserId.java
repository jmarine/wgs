package com.github.jmarine.wampservices.wsg;


public class UserId implements java.io.Serializable
{
    private String uid;
    private String openIdConnectProviderUrl;    
    
    public UserId() { }
    
    public UserId(String uid, String openIdConnectProviderUrl) 
    {
        this.uid = uid;
        this.openIdConnectProviderUrl = openIdConnectProviderUrl;
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
    

    public boolean equals(Object o) { 
        if( (o != null) && (o instanceof UserId) ) {
            UserId pk = (UserId)o;
            return uid.equals(pk.getUid()) && openIdConnectProviderUrl.equals(pk.getOpenIdConnectProviderUrl());
        } else {
            return false;
        }
    }

    public int hashCode() { 
        return (openIdConnectProviderUrl + "#" + uid).hashCode(); 
    }
    
}
