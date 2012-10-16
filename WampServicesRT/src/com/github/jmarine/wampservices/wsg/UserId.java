package com.github.jmarine.wampservices.wsg;


public class UserId implements java.io.Serializable
{
    private String nick;
    private String openIdProviderUrl;    
    
    public UserId() { }
    
    public UserId(String nick, String openIdProviderUrl) 
    {
        this.nick = nick;
        this.openIdProviderUrl = openIdProviderUrl;
    }
    
    /**
     * @return the nick
     */
    public String getNick() {
        return nick;
    }

    /**
     * @param nick the nick to set
     */
    public void setNick(String nick) {
        this.nick = nick;
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
            UserId uid = (UserId)o;
            return nick.equals(uid.getNick()) && openIdProviderUrl.equals(uid.getOpenIdProviderUrl());
        } else {
            return false;
        }
    }

    public int hashCode() { 
        return (openIdProviderUrl + "#" + nick).hashCode(); 
    }
    
}
