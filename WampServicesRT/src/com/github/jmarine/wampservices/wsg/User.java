package com.github.jmarine.wampservices.wsg;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.TemporalType;


@Entity
@IdClass(UserId.class)
@Table(name="USR")
@NamedQueries({
    @NamedQuery(name="wsg.findUserByNick",query="SELECT OBJECT(u) FROM User u WHERE u.nick = :nick"),
    @NamedQuery(name="wsg.findUserByEmail",query="SELECT OBJECT(u) FROM User u WHERE u.email = :email")
})
public class User implements Serializable 
{
    @Id
    @Column(name="nick")
    private String nick;
    
    @Id
    @Column(name="oid_provider")
    private String openIdProviderUrl;
    
    @Column(name="name",nullable=false)
    private String name;   
    
    @Column(name="password",nullable=false)
    private String password;
    
    @Column(name="is_admin")
    private boolean administrator;

    @javax.persistence.Temporal(TemporalType.TIMESTAMP)
    @Column(name="profile_caducity")
    private java.util.Calendar profileCaducity;
    
    @Column(name="email",unique=false)
    private String email;
    
    @Column(name="picture")
    private String picture;    


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
     * @return the OpenId Connect Provider
     */
    public String getOpenIdProviderUrl() {
        return openIdProviderUrl;
    }

    /**
     * @param openIdProvider the URL of the issuer to set
     */
    public void setOpenIdProviderUrl(String openIdProviderUrl) {
        this.openIdProviderUrl = openIdProviderUrl;
    }
    
    
    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }
    

    /**
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @return the administrator
     */
    public boolean isAdministrator() {
        return administrator;
    }

    /**
     * @param administrator the administrator to set
     */
    public void setAdministrator(boolean administrator) {
        this.administrator = administrator;
    }

    
        /**
     * @return the expiration date of the user
     */
    public java.util.Calendar getProfileCaducity() {
        return profileCaducity;
    }

    /**
     * @param profileCaducity the caducity timestamp for the profile data to set
     */
    public void setProfileCaducity(java.util.Calendar profileCaducity) {
        this.profileCaducity = profileCaducity;
    }    

    
    /**
     * @return the email
     */
    public String getEmail() {
        return email;
    }

    /**
     * @param email the email to set
     */
    public void setEmail(String email) {
        this.email = email;
    }
    
    
    /**
     * @return the URL of the picture
     */
    public String getPicture() {
        return picture;
    }

    /**
     * @param picture the URL of the picture to set
     */
    public void setPicture(String picture) {
        this.picture = picture;
    }    
    
}
