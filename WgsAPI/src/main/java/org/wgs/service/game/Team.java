package org.wgs.service.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.wgs.security.User;
import java.util.List;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;

@Entity
@Table(name="TEAM")
@NamedQueries({
    @NamedQuery(name="wgs.findTeamByAliasAndDomain",query="SELECT OBJECT(t) FROM Team t WHERE t.alias = ?1 AND t.domain = ?2")
})
public class Team 
{
    @Id
    @Column(name="id", nullable = false, length=36)
    private String id;
    
    @Column(name="groupAlias", nullable = true)
    private String alias;
    
    @Column(name="domain", nullable = true)    
    private String domain;     
    
    @ManyToOne(fetch=FetchType.LAZY)
    private User owner;
    
    @OneToMany
    @JoinTable(name = "TEAM_MEMBER", inverseJoinColumns = @JoinColumn(name = "MEMBER_UID") )
    @OrderColumn(name = "position")
    private List<User> members;

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the alias
     */
    public String getAlias() {
        return alias;
    }

    /**
     * @param alias the alias to set
     */
    public void setAlias(String alias) {
        this.alias = alias;
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
     * @return the owner
     */
    public User getOwner() {
        return owner;
    }

    /**
     * @param owner the owner to set
     */
    public void setOwner(User owner) {
        this.owner = owner;
    }
   

    /**
     * @return the members
     */
    public List<User> getMembers() {
        return members;
    }

    /**
     * @param members the members to set
     */
    public void setMembers(List<User> members) {
        this.members = members;
    }
    
    
    public WampDict toWampObject()
    {
        WampDict retval = new WampDict();
        retval.put("teamId", getId());
        retval.put("teamName", getAlias());
        
        WampList teamUsers = new WampList();
        for(User user : getMembers()) {
            teamUsers.add(user.toWampObject(false));
        }
        retval.put("users", teamUsers);
        
        return retval;
    }    

}

