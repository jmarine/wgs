package org.wgs.service.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.wgs.security.User;
import java.util.List;

@Entity
@Table(name="TEAM")
@NamedQueries({
    @NamedQuery(name="wgs.findByAlias",query="SELECT OBJECT(t) FROM Team t WHERE t.alias = ?1")
})
public class Team 
{
    @Id
    @Column(name="id", nullable = false, length=36)
    private String id;
    
    @Column(name="groupAlias", nullable = true)
    private String alias;
    
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

}

