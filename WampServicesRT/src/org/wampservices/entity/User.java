package org.wampservices.entity;

import java.io.Serializable;
import java.security.Principal;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.TemporalType;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;


@Entity
@Table(name="USR")
@NamedQueries({
    @NamedQuery(name="wgs.findUsersByEmail",query="SELECT OBJECT(u) FROM User u WHERE u.email = :email")
})
public class User implements Serializable, Principal
{
    @EmbeddedId
    private UserId id;
    
    @Column(name="name",nullable=false)
    private String name;   
    
    @Column(name="password")
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
     * @return the UserPK
     */
    public UserId getId() {
        return id;
    }

    /**
     * @param id the userId to set
     */
    public void setId(UserId id) {
        this.id = id;
    }    
    
    /**
     * @return the UserPK
     */
    public String getFQid() {
        return id.toString();
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
    
    
    @Override
    public boolean equals(Object o) 
    { 
        if( (o != null) && (o instanceof User) ) {
            User u = (User)o;
            return id.equals(u.getId());
        } else {
            return false;
        }
    }

    @Override
    public String toString()
    {   
        return id.toString();
    }
    
    @Override
    public int hashCode()
    {
        return id.hashCode();
    }

    public ObjectNode toJSON() 
    {
        ObjectMapper mapper = new ObjectMapper();        
        ObjectNode retval = mapper.createObjectNode();
        retval.put("user", id.toString());
        retval.put("name", getName());
        retval.put("picture", getPicture());    
        return retval;
    }
}
