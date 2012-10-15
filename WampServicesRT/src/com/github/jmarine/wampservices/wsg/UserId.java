/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.jmarine.wampservices.wsg;


/**
 *
 * @author jordi
 */
public class UserId implements java.io.Serializable
{
    private String nick;
    private String domain;    
    
    
    public UserId(String nick, String domain) 
    {
        this.nick = nick;
        this.domain = domain;
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
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the domain to set
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }
    

    public boolean equals(Object o) { 
        if( (o != null) && (o instanceof UserId) ) {
            UserId uid = (UserId)o;
            return nick.equals(uid.getNick()) && domain.equals(uid.getDomain());
        } else {
            return false;
        }
    }

    public int hashCode() { 
        return (domain + "#" + nick).hashCode(); 
    }
    
}
