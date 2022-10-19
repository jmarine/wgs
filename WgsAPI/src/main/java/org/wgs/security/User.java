package org.wgs.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.TemporalType;

import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


@Entity
@Table(name="USR")
@NamedQueries({
    @NamedQuery(name="wgs.findUsersByLoginAndDomain",query="SELECT OBJECT(u) FROM User u WHERE u.login = ?1 and u.domain = ?2"),
    @NamedQuery(name="wgs.findUsersByEmail",query="SELECT OBJECT(u) FROM User u WHERE u.email = ?1")
})
public class User implements Serializable, Principal
{
    private static final long serialVersionUID = 0L;
    
    @Id
    @Column(name="uid", nullable = false, length=36)
    private String uid;

    @Column(name="domain", nullable = false)    
    private String domain; 
    
    @Column(name="login", nullable = false)    
    private String login;
    
    @Column(name="name", nullable=false)
    private String name;   

    @jakarta.persistence.Temporal(TemporalType.TIMESTAMP)
    @Column(name="last_login")
    private java.util.Calendar lastLoginTime;
    
    @Column(name="password")
    private String password;
    
    @Column(name="is_admin")
    private boolean administrator;

    @jakarta.persistence.Temporal(TemporalType.TIMESTAMP)
    @Column(name="profile_caducity")
    private java.util.Calendar profileCaducity;
    
    @Column(name="email",unique=false)
    private String email;
    
    @Column(name="email_valid")
    private boolean emailValidated;
    
    @Column(name="picture")
    private String picture;    

    @jakarta.persistence.Temporal(TemporalType.TIMESTAMP)
    @Column(name="token_caducity")
    private java.util.Calendar tokenCaducity;
    
    @Column(name="access_token", length=2048)
    private String accessToken;
    
    @Column(name="refresh_token", length=2048)
    private String refreshToken;    
    
    @ManyToMany(fetch = FetchType.LAZY)
    @OrderBy(value = "name")
    @JoinTable(name="USR_FRIEND", 
            joinColumns={@JoinColumn(name="uid", referencedColumnName = "uid")}, 
            inverseJoinColumns={@JoinColumn(name="friend_uid", referencedColumnName = "uid")})
    private List<User> friends;

    
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
     * @return the login
     */
    public String getLogin() {
        return login;
    }

    /**
     * @param login the login to set
     */
    public void setLogin(String login) {
        this.login = login;
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
     * @return the lastLoginTime
     */
    public java.util.Calendar getLastLoginTime() {
        return lastLoginTime;
    }

    /**
     * @param lastLoginTime the lastLoginTime to set
     */
    public void setLastLoginTime(java.util.Calendar lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
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
     * @return the emailValidated
     */
    public boolean isEmailValidated() {
        return emailValidated;
    }

    /**
     * @param emailValidated the emailValidated to set
     */
    public void setEmailValidated(boolean emailValidated) {
        this.emailValidated = emailValidated;
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


    
    /**
     * @return the tokenCaducity
     */
    public java.util.Calendar getTokenCaducity() {
        return tokenCaducity;
    }

    /**
     * @param tokenCaducity the tokenCaducity to set
     */
    public void setTokenCaducity(java.util.Calendar tokenCaducity) {
        this.tokenCaducity = tokenCaducity;
    }

    
    /**
     * @return the accessToken
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * @param accessToken the accessToken to set
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    
    /**
     * @return the refreshToken
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * @param refreshToken the refreshToken to set
     */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    
    public List<User> getFriends() 
    {
        return this.friends;
    }
    
    public void setFriends(List<User> friends) 
    {
        this.friends = friends;
    }  
    
    
    @Override
    public boolean equals(Object o) 
    { 
        if( (o != null) && (o instanceof User) ) {
            User u = (User)o;
            return uid.equals(u.getUid());
        } else {
            return false;
        }
    }

    @Override
    public String toString()
    {   
        return uid;
    }
    
    @Override
    public int hashCode()
    {
        return uid.hashCode();
    }

    public WampDict toWampObject(boolean includeFriends) 
    {
        WampDict retval = new WampDict();
        retval.put("user", uid);
        retval.put("name", getName());
        retval.put("picture", getPicture());    

        if(includeFriends && getFriends() != null) {
            WampList friends = new WampList();
            for(User friend : getFriends()) {
                if(friend.getLastLoginTime() != null) {
                    friends.add(friend.toWampObject(false));
                }
            }
            retval.put("friends", friends);
        }
        
        return retval;
    }

}
