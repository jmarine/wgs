package org.wampservices.wgs;

import org.wampservices.entity.User;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import javax.persistence.Transient;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;


@Entity(name="GroupMember")
@Table(name="APP_GROUP_MEMBER")
public class Member implements java.io.Serializable
{
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="gid", referencedColumnName="gid")
    private Group  applicationGroup;

    @Id
    @Column(name="slot")
    private int    slot;

    @Column(name="userType")
    private String userType;
    
    @ManyToOne(fetch= FetchType.EAGER)
    @JoinColumns({
        @JoinColumn(name="uid", referencedColumnName = "uid"),
        @JoinColumn(name="oic_provider", referencedColumnName = "oic_provider")
    })      
    private User   user;
    
    @ManyToOne(fetch=FetchType.EAGER)
    @JoinColumns({
        @JoinColumn(name="role_app", referencedColumnName = "app"),
        @JoinColumn(name="role_name", referencedColumnName = "name")
    })      
    private Role   role;
    
    @Column(name="team")
    private int    team;
    
    @Column(name="state")
    @Enumerated(EnumType.ORDINAL)
    private MemberState state;

    @Transient
    private Client client;
    
    
    public Member()
    {
        state = MemberState.EMPTY;
    }
    
    /**
     * @return the client
     */
    public Group getApplicationGroup() {
        return applicationGroup;
    }

    /**
     * @param applicationGroup the application Group to set
     */
    public void setApplicationGroup(Group applicationGroup) {
        this.applicationGroup = applicationGroup;
    }    
    
    /**
     * @return the slot
     */
    public int getSlot() {
        return slot;
    }

    /**
     * @param slot the slot to set
     */
    public void setSlot(int slot) {
        this.slot = slot;
    }    
    
    /**
     * @return the client
     */
    public Client getClient() {
        return client;
    }

    /**
     * @param client the client to set
     */
    public void setClient(Client client) {
        this.client = client;
    }

    /**
     * @return the usertype
     */
    public String getUserType() {
        return userType;
    }

    /**
     * @param usertype the usertype to set
     */
    public void setUserType(String userType) {
        this.userType = userType;
    }

    /**
     * @return the user
     */
    public User getUser() {
        return user;
    }

    /**
     * @param user the user to set
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * @return the role
     */
    public Role getRole() {
        return role;
    }

    /**
     * @param role the role to set
     */
    public void setRole(Role role) {
        this.role = role;
    }

    /**
     * @return the team
     */
    public int getTeam() {
        return team;
    }

    /**
     * @param team the team to set
     */
    public void setTeam(int team) {
        this.team = team;
    }
    
    /**
     * @return the state
     */
    public MemberState getState() {
        return state;
    }

    /**
     * @param state the state to set
     */
    public void setState(MemberState state) {
        this.state = state;
    }    
    
    
    public ObjectNode toJSON() 
    {
        boolean connected = (getClient() != null);
        
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode obj = mapper.createObjectNode();
        obj.put("sid", ((client!=null)? client.getSessionId():""));
        obj.put("user", ((user!=null)? user.getFQid() : ((client != null) ? "#anonymous-" + client.getSessionId() : "") ) );
        obj.put("name", ((user!=null)? user.getName() : ((client != null) ? "Anonymous" : "") ) );
        obj.put("picture", ((user!=null)? user.getPicture() : ((client != null) ? "images/anonymous.png": "") ) );
        obj.put("type",userType);
        obj.put("state",String.valueOf(state));
        obj.put("role",((role!=null)? role.getName():""));
        obj.put("team",team);
        obj.put("slot", slot);
        obj.put("connected", connected);
        return obj;
    }
    
    
}