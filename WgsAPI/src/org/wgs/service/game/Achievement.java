
package org.wgs.service.game;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Calendar;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.TemporalType;

import org.wgs.security.User;


@Entity
@Table(name="APP_ACHIEVEMENT")
public class Achievement implements Serializable
{
    private static final long serialVersionUID = 0L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
 
    @javax.persistence.Temporal(TemporalType.TIMESTAMP)
    @Column(name="t")
    private Calendar when;
    
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name = "app", referencedColumnName = "id")
    private Application app;
    
    @Column(name="gid", length=36, nullable = true)
    private String gid;
    
    @Column(name="name", length=10)
    private String name;  // i.e: WIN, DRAW, LOSE, RESIGN, TIME, KILLED PIECES, etc.

    @Column(name="val", length=50, nullable = true)
    private String value; // i.e: opponent uid
    

    @ManyToOne(fetch= FetchType.EAGER)
    @JoinColumns({@JoinColumn(name="uid", referencedColumnName = "uid")})
    private User sourceUser;

    @ManyToOne(fetch=FetchType.EAGER, optional = true)
    @JoinColumns({
        @JoinColumn(name="app", referencedColumnName = "app", insertable = false, updatable = false),
        @JoinColumn(name="role_name", referencedColumnName = "name")
    })      
    private Role sourceRole;    
    

    
    /**
     * @return the id
     */
    public Long getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * @return the when
     */
    public Calendar getWhen() {
        return when;
    }

    /**
     * @param when the when to set
     */
    public void setWhen(Calendar when) {
        this.when = when;
    }

    /**
     * @return the app
     */
    public Application getApp() {
        return app;
    }

    /**
     * @param app the app to set
     */
    public void setApp(Application app) {
        this.app = app;
    }

    /**
     * @return the sourceUser
     */
    public User getSourceUser() {
        return sourceUser;
    }

    /**
     * @param sourceUser the sourceUser to set
     */
    public void setSourceUser(User sourceUser) {
        this.sourceUser = sourceUser;
    }

    /**
     * @return the sourceRole
     */
    public Role getSourceRole() {
        return sourceRole;
    }

    /**
     * @param sourceRole the sourceRole to set
     */
    public void setSourceRole(Role sourceRole) {
        this.sourceRole = sourceRole;
    }



    /**
     * @return the gid
     */
    public String getGid() {
        return gid;
    }

    /**
     * @param gid the gid to set
     */
    public void setGid(String gid) {
        this.gid = gid;
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
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * @param value the value to set
     */
    public void setValue(String value) {
        this.value = value;
    }
    
}
