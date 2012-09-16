
package com.github.jmarine.wampservices.wsg;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Transient;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;



@Entity
@NamedQueries({
    @NamedQuery(name="wsg.findAllApps",query="SELECT OBJECT(a) FROM Application a"),
    @NamedQuery(name="wsg.findAppById",query="SELECT OBJECT(a) FROM Application a WHERE a.id = :id"),
    @NamedQuery(name="wsg.findAppBySpec",query="SELECT OBJECT(a) FROM Application a WHERE a.name = :name AND a.domain = :domain AND a.version = :version")
})
public class Application implements Serializable {
    
    @Id
    @Column(name="id", length=50)
    private String  id;

    @Column(name="name", length=50)
    private String  name;
    
    @Column(name="appdomain", length=50)
    private String  domain;
    
    @Column(name="appversion")
    private int     version;
    
    @ManyToOne(fetch= FetchType.EAGER)
    @JoinColumn(name="adminuser")
    private User  adminUser;
    
    @Column(name="description", length=100)
    private String  description;
    
    @Column(name="ai")
    private boolean aiAvailable;
   
    @Column(name="minMembers")
    private int     minMembers;
    
    @Column(name="maxMembers")
    private int     maxMembers;
    
    @Column(name="deltaMembers")
    private int deltaMembers;
    
    @Column(name="dyn")
    private boolean dynamicGroup;
    
    @Column(name="alliances")
    private boolean alliancesAllowed;
    
    @Column(name="observable")
    private boolean observableGroup;

    @OneToMany(mappedBy = "application", fetch=FetchType.EAGER, cascade = { CascadeType.ALL })
    private Collection<Role> roles = new ArrayList<Role>();
    
    @Transient
    private HashMap<String,Group> groupsByGid = new HashMap<String,Group>();

    @Transient
    private Queue<Group> openGroupsForAutoMatch = new LinkedList<Group>();

    
    public void setAppId(String id) {
        this.id = id;
    }
    
    public String getAppId() {
        return id;
    }
    
    /**
     * @return the version
     */
    public int getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(int version) {
        this.version = version;
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
     * @return the admin_user
     */
    public User getAdminUser() {
        return adminUser;
    }

    /**
     * @param admin_nick the admin_nick to set
     */
    public void setAdminUser(User adminUser) {
        this.adminUser = adminUser;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the aiAvailable
     */
    public boolean isAIavailable() {
        return aiAvailable;
    }

    /**
     * @param aiAvailable the aiAvailable to set
     */
    public void setAIavailable(boolean aiAvailable) {
        this.aiAvailable = aiAvailable;
    }

    /**
     * @return the minMembers
     */
    public int getMinMembers() {
        return minMembers;
    }

    /**
     * @param minMembers the minMembers to set
     */
    public void setMinMembers(int minMembers) {
        this.minMembers = minMembers;
    }

    /**
     * @return the maxGroupMembers
     */
    public int getMaxMembers() {
        return maxMembers;
    }

    /**
     * @param maxMembers the maxMembers to set
     */
    public void setMaxMembers(int maxMembers) {
        this.maxMembers = maxMembers;
    }
    
    /**
     * @return the deltaMembers
     */
    public int getDeltaMembers() {
        return deltaMembers;
    }

    /**
     * @param deltaMembers the deltaMembers to set
     */
    public void setDeltaMembers(int deltaMembers) {
        this.deltaMembers = deltaMembers;
    }    

    /**
     * @return the dynamicGroup
     */
    public boolean isDynamicGroup() {
        return dynamicGroup;
    }

    /**
     * @param dynamicGroup the dynamicGroup to set
     */
    public void setDynamicGroup(boolean dynamicGroup) {
        this.dynamicGroup = dynamicGroup;
    }

    /**
     * @return the alliancesAllowed
     */
    public boolean isAlliancesAllowed() {
        return alliancesAllowed;
    }

    /**
     * @param alliancesAllowed the alliancesAllowed to set
     */
    public void setAlliancesAllowed(boolean alliancesAllowed) {
        this.alliancesAllowed = alliancesAllowed;
    }

    /**
     * @return the observableGroup
     */
    public boolean isObservableGroup() {
        return observableGroup;
    }

    /**
     * @param observableGroup the observableGroup to set
     */
    public void setObservableGroup(boolean observableGroup) {
        this.observableGroup = observableGroup;
    }

    /**
     * @return the groupsByGid
     */
    public HashMap<String,Group> getGroupsByGid() {
        return groupsByGid;
    }

    /**
     * @param groupsByGid the groupsByGid to set
     */
    public void setGroupsByGid(HashMap<String,Group> groupsByGid) {
        this.groupsByGid = groupsByGid;
    }

    /**
     * @return the roles
     */
    public Collection<Role> getRoles() {
        return roles;
    }

    /**
     * @param roles the roles to set
     */
    public void setRoles(Collection<Role> roles) {
        this.roles = roles;
    }
    

    public void addAutoMatchGroup(Group g)
    {
        boolean valid = (g == null) || (g.isAutoMatchEnabled() && !g.isAutoMatchCompleted() && g.getState()==GroupState.OPEN);
        if(valid) openGroupsForAutoMatch.add(g);
    }
    
    public Group getNextAutoMatchGroup()
    {
        Group g = null;
        boolean valid = false;
        
        while(!valid) {
            g = openGroupsForAutoMatch.peek();
            valid = (g == null) || (g.isAutoMatchEnabled() && !g.isAutoMatchCompleted() && g.getState()==GroupState.OPEN);
            if(g!=null && !valid) openGroupsForAutoMatch.poll();
        } 
        
        return g;
    }
    
    public Collection<Group> getGroupsByState(GroupState state)
    {
        ArrayList<Group> retval = new ArrayList<Group>();
        for(Group g : groupsByGid.values()) {
            if(state==null || state==g.getState()) {
                retval.add(g);
            }
        }
        return retval;
    }

    public void addGroup(Group grp)
    {
        groupsByGid.put(grp.getGid(), grp);
        if(grp.isAutoMatchEnabled()) {
            addAutoMatchGroup(grp);
        }
    }

    public void removeGroup(Group grp)
    {
        groupsByGid.remove(grp.getGid());
        openGroupsForAutoMatch.remove(grp);
    }

    public void addRole(Role r)
    {
        roles.add(r);
    }
    
    
    public Role getRoleByName(String roleName)
    {
        Role retval = null;
        for(Role role : roles) {
            if(roleName.equals(role.getName())) {
                retval = role;
                break;
            }
        }
        return retval;
    }
    
    public ObjectNode toJSON()
    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode obj = mapper.createObjectNode();
        try {
            obj.put("appId", getAppId());
            obj.put("name", getName());
            obj.put("domain", getDomain());
            obj.put("ai", isAIavailable());
            obj.put("version", getVersion());
            
            // obj.put("open", getGroupsByState(GroupState.OPEN).size());
            // obj.put("started", getGroupsByState(GroupState.STARTED).size());
            
        } catch(Exception ex) { }
        return obj;
    }
    
    @Override
    public String toString()
    {
        return toJSON().toString();        
    }
    
}
