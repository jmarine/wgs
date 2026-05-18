package org.wgs.service.game;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.wgs.security.User;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;



@Entity
@Table(name="APPLICATION")
@NamedQueries({
    @NamedQuery(name="wgs.findAllApps",query="SELECT OBJECT(a) FROM Application a"),
    @NamedQuery(name="wgs.findAppById",query="SELECT OBJECT(a) FROM Application a WHERE a.id = ?1"),
    @NamedQuery(name="wgs.findAppByName",query="SELECT OBJECT(a) FROM Application a WHERE a.name = ?1 ORDER BY a.version DESC")

})
public class Application implements Serializable, Comparable
{
    private static final long serialVersionUID = 0L;
    
    private static final Logger logger = Logger.getLogger(Application.class.toString());
    
    
    @Id
    @Column(name="id", length=36)
    private String  id;

    @Column(name="name", length=50)
    private String  name;
    
    @Column(name="appdomain", length=50)
    private String  domain;
    
    @Column(name="appversion")
    private int     version;
    
    @ManyToOne(fetch= FetchType.EAGER)
    @JoinColumns({@JoinColumn(name="admin_uid", referencedColumnName = "uid")})    
    private User  adminUser;
    
    @Column(name="description", length=100)
    private String  description;
    
    @Column(name="action_validator", length=255)
    private String  actionValidator;    
    
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
    @OrderColumn(name="position")
    private List<Role> roles = new ArrayList<Role>();

    @Column(name="max_scores")
    private int maxScores;    

    @Column(name="desc_scores")
    private boolean descendingScoreOrder;
    
    @Column(name="internal_data_type")
    private String internalDataClass;
    
    @Lob
    @Column(name="internal_data_options")
    private String internalDataOptionsJson;
    
    @Transient
    private Map<String,Object> internalDataOptions;      
    
    
    @Transient
    private HashMap<String,Group> groupsByGid = new HashMap<String,Group>();


    
    @PostLoad
    public void convertInternDataJsonToObject()
    {
        this.internalDataOptions = null;
        
        if(getInternalDataOptionsJson() != null) {
            try {
                Jsonb jsonb = JsonbBuilder.create();

                HashMap<String,Object> obj = new HashMap<String,Object>();
                obj = jsonb.fromJson(getInternalDataOptionsJson(), obj.getClass());
                setInternalDataOptions(obj); 
                logger.finest("Application.convertInternDataJsonToObject: internal data object state loaded successfully");
                
            } catch(Exception ex) {
                logger.severe("Application.convertInternDataJsonToObject: error loading internal data object state: " + ex.getMessage());
                logger.throwing(Application.class.getName(), "convertInternDataJsonToObject", ex);
            }
        }
    }
    
    
    //@PrePersist
    //@PreUpdate
    public void convertInternalDataOptionsToJSON()
    {
        this.internalDataOptionsJson = null;
        
        if(internalDataOptions != null) {
            try { 
                Jsonb jsonb = JsonbBuilder.create();
                this.internalDataOptionsJson = jsonb.toJson(getInternalDataOptions());
            } catch(Exception ex) {
                logger.severe("Application.convertInternalDataOptionsToJSON: error serializing JSON object: " + ex.getMessage());
                logger.throwing(Application.class.getName(), "convertInternalDataOptionsToJSON", ex);
            }
        }        
    }    

    
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
     * @param admin_user the admin_user to set
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
     * @return the actionValidator
     */
    public String getActionValidator() {
        return actionValidator;
    }

    /**
     * @param actionValidator the actionValidator to set
     */
    public void setActionValidator(String actionValidator) {
        this.actionValidator = actionValidator;
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
    public List<Role> getRoles() {
        return roles;
    }

    /**
     * @param roles the roles to set
     */
    public void setRoles(List<Role> roles) {
        this.roles = roles;
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
    }

    public void removeGroup(Group grp)
    {
        groupsByGid.remove(grp.getGid());
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
    
    
    /**
     * @return the maxScores
     */
    public int getMaxScores() {
        return maxScores;
    }

    /**
     * @param maxScores the maxScores to set
     */
    public void setMaxScores(int maxScores) {
        this.maxScores = maxScores;
    }

    /**
     * @return the descendingScoreValues
     */
    public boolean isDescendingScoreOrder() {
        return descendingScoreOrder;
    }
    

    /**
     * @param descendingScoreOrder the descendingScoreValues to set
     */
    public void setDescendingScoreOrder(boolean descendingScoreOrder) {
        this.descendingScoreOrder = descendingScoreOrder;
    }    
    
    /**
     * @return the internalDataClass
     */
    public String getInternalDataClass() {
        return internalDataClass;
    }

    /**
     * @param internalDataClass the intenalDataClass to set
     */
    public void setInternalDataClass(String internalDataClass) {
        this.internalDataClass = internalDataClass;
    }
    
    /**
     * @return the internalDataOptions
     */
    public Map<String,Object> getInternalDataOptions() {
        return internalDataOptions;
    }

    /**
     * @param internalDataOptions the internalDataOptions to set
     */
    public void setInternalDataOptions(Map<String,Object> internalDataOptions) {
        this.internalDataOptions = internalDataOptions;
        convertInternalDataOptionsToJSON();
    }

    
    
    public WampDict toWampObject()
    {
        WampDict obj = new WampDict();
        obj.put("appId",    getAppId());
        obj.put("name",     getName());
        obj.put("domain",   getDomain());
        obj.put("ai",       isAIavailable());
        obj.put("version",  getVersion());
        obj.put("roles",    getRolesNode());
        obj.put("internalDataClass", getInternalDataClass());
        obj.put("internalDataOptions", obj.castToWampObject(getInternalDataOptions()));

        return obj;
    }

    
    public WampList getRolesNode() 
    {
        WampList array = new WampList();
        if(roles != null) {
            for(Role role : roles) {
                array.add(role.toWampObject());
            }
        }
        return array;
    }    
    
    
    @Override
    public boolean equals(Object o)
    {
        if(o != null && o instanceof Application) {
            Application app = (Application)o;
            return (id.equals(app.getAppId()));
        } else {
            return false;
        }
    }
    
    
    @Override
    public int hashCode()
    {
        return id.hashCode();
    }

    @Override
    public int compareTo(Object t) {
        if(t != null && t instanceof Application) {
            Application app = (Application)t;
            return getName().compareToIgnoreCase(app.getName());
        } else {
            return 1;
        }
    }

    /**
     * @return the internalDataOptionsJson
     */
    public String getInternalDataOptionsJson() {
        return internalDataOptionsJson;
    }

    /**
     * @param internalDataOptionsJson the internalDataOptionsJson to set
     */
    public void setInternalDataOptionsJson(String internalDataOptionsJson) {
        this.internalDataOptionsJson = internalDataOptionsJson;
    }


}
