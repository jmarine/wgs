package com.github.jmarine.wampservices.wsg;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;


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
    @Column(name="domain")
    private String domain;
    
    @Column(name="expires")
    private long expires;
    
    @Column(name="name",nullable=false)
    private String name;   
    
    @Column(name="password",nullable=false)
    private String password;
    
    @Column(name="adminrole")
    private boolean administrator;
    
    @Column(name="email",unique=false)
    private String email;

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
    
    /**
     * @return the expiration date of the user
     */
    public long getExpires() {
        return expires;
    }

    /**
     * @param expires the expiration date to set
     */
    public void setExpires(long expires) {
        this.expires = expires;
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
    
}
