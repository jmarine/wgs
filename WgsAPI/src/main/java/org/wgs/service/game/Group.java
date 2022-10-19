package org.wgs.service.game;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.Cacheable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.wgs.security.User;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


@Entity(name="AppGroup")
@Table(name="APP_GROUP")
@Cacheable(false)
@NamedQueries({
    @NamedQuery(name="wgs.findAllGroups",query="SELECT OBJECT(g) FROM AppGroup g"),
    @NamedQuery(name="wgs.findFinishedGroupsFromUser",query="SELECT DISTINCT OBJECT(g) FROM AppGroup g, IN(g.members) m WHERE g.state = org.wgs.service.game.GroupState.FINISHED AND m.user = ?1")
})
// @org.eclipse.persistence.annotations.Index(name="APP_GROUP_STATE_IDX", columnNames={"STATE"})
public class Group implements java.io.Serializable
{
    private static final long serialVersionUID = 0L;
    
    @Id
    @Column(name="gid", nullable = false, length=36)
    private String gid;
    
    @Column(name="description")
    private String description;
    
    //@Lob()
    @Column(name="data")
    private String data;
    
    @Column(name="initial_data")
    private String initialData;    
    
    @Column(name="internal_data")
    private String internalData;     
    
    @Column(name="password")
    private String password;
    
    @Enumerated(EnumType.ORDINAL)
    private GroupState state;
    
    @Column(name="turn")
    private Integer turn;
    
    @Column(name="minMembers")
    private int  minMembers;
    
    @Column(name="maxMembers")
    private int  maxMembers;
    
    @Column(name="deltaMembers")
    private int  deltaMembers;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="admin_uid")    
    private User admin;
    

    @Column(name="automatchEnabled")
    private boolean autoMatchEnabled;
    
    @Column(name="automatchCompleted")
    private boolean autoMatchCompleted;
    
    @Column(name="dyn")
    private boolean dynamicGroup;
    
    @Column(name="alliances")
    private boolean alliancesAllowed;
    
    @Column(name="observable")
    private boolean observableGroup;
    
    @Column(name="hidden")
    private boolean hidden;

    @OneToMany(mappedBy = "applicationGroup", fetch=FetchType.EAGER, cascade = { CascadeType.ALL }, orphanRemoval = true)
    @OrderBy("slot")
    private List<Member> members = new ArrayList<Member>();
    
    @OneToMany(mappedBy = "applicationGroup", fetch=FetchType.LAZY, cascade = { CascadeType.ALL }, orphanRemoval = true)
    @OrderBy("actionOrder")
    private List<GroupAction> actions = new ArrayList<GroupAction>();    

    @ManyToOne(fetch=FetchType.LAZY)
    private Application application;

    @Version
    private Long version;
    
    /**
     * @return the state
     */
    public GroupState getState() {
        return state;
    }

    /**
     * @param state the state to set
     */
    public void setState(GroupState state) {
        this.state = state;
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
     * @return the maxMembers
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
     * @return the multipleMembers
     */
    public int getDeltaMembers() {
        return deltaMembers;
    }

    /**
     * @param deltaMembers the multipleMembers to set
     */
    public void setDeltaMembers(int deltaMembers) {
        this.deltaMembers = deltaMembers;
    }    
    
    /**
     * @return the admin_user
     */
    public User getAdmin() {
        return admin;
    }

    /**
     * @param admin the admin to set
     */
    public void setAdmin(User admin) {
        this.admin = admin;
    }

    
    /**
     * @return the autoMatchEnabled property
     */
    public boolean isAutoMatchEnabled() {
        return autoMatchEnabled;
    }

    /**
     * @param autoMatchEnabled set the autoMatchEnabled property
     */
    public void setAutoMatchEnabled(boolean autoMatchEnabled) {
        this.autoMatchEnabled = autoMatchEnabled;
    }    
    

    /**
     * @return the autoMatchCompleted property
     */
    public boolean isAutoMatchCompleted() {
        return autoMatchCompleted;
    }

    /**
     * @param autoMatchCompleted set the autoMatchCompleted property
     */
    public void setAutoMatchCompleted(boolean autoMatchCompleted) {
        this.autoMatchCompleted = autoMatchCompleted;
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
     * @return the data
     */
    public String getData() {
        return data;
    }

    /**
     * @param data the data to set
     */
    public void setData(String data) {
        this.data = data;
    }

    /**
     * @return the initialData
     */
    public String getInitialData() {
        return initialData;
    }

    /**
     * @param initialData the initialData to set
     */
    public void setInitialData(String initialData) {
        this.initialData = initialData;
    }

    
    /**
     * @return the internalData
     */
    public String getInternalData() {
        return internalData;
    }

    /**
     * @param internalData the internalData to set
     */
    public void setInternalData(String internalData) {
        this.internalData = internalData;
    }
    
    
    /**
     * @return the app
     */
    public Application getApplication() {
        return application;
    }

    /**
     * @param app the app to set
     */
    public void setApplication(Application app) {
        this.application = app;
    }
    
    
    
    public int getNumMembers()  
    {
        int count = 0;
        for(int i = 0; i < members.size(); i++) {
            Member member = getMember(i);
            if( (member != null) && (member.getClientSID() != null) ) count++;
        }
        return count;
    }
    
    
    public int getAvailSlots() 
    {
        if(maxMembers <= 0) {
            return -1;
        } else {
            int count = 0;
            for(int i = 0; i < members.size(); i++) {
                Member member = getMember(i);
                if( (member != null) && (member.getUser() != null) ) count++;
            }            
            return maxMembers - count;
        }
    }
    
    
    public int getNumSlots() 
    {
        return members.size();
    }

    
    public List<Member> getMembers() 
    {
        return this.members;
    }
    
    public void setMembers(List<Member> members) 
    {
        this.members = new ArrayList<Member>();
        for(Member member : members) {
            setMember(member.getSlot(), member);
        }
    }    
    
    public Member getMember(int index) 
    {
        if(index < getNumSlots()) {
            return members.get(index);
        } else {
            return null;
        }
    }

    public void setMember(int index, Member member) 
    {
        while(index >= members.size()) {
            members.add(null);
        }
        member.setSlot(index);
        members.set(index, member);
    }

    public Member removeMember(int index) {
        Member removed = getMember(index);
        int last = getNumSlots();
        for(; index < last-1; index++) {
            setMember(index, getMember(index+1));
        }      
        if(index < last) members.remove(index);
        return removed;
    }
    
    
    public List<GroupAction> getActions() 
    {
        return this.actions;
    }
    
    public void setActions(List<GroupAction> actions) 
    {
        this.actions = actions;
    }        
    
    /**
     * @return the hidden
     */
    public boolean isHidden() {
        return hidden;
    }

    /**
     * @param hidden the hidden to set
     */
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
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
     * @return the turn
     */
    public Integer getTurn() {
        return turn;
    }

    /**
     * @param turn the turn to set
     */
    public void setTurn(Integer turn) {
        this.turn = turn;
    }
    
    
    
    /**
     * @return the version
     */
    public Long getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o)
    {
        if(o != null && o instanceof Group) {
            Group group = (Group)o;
            return gid.equals(group.gid);
        } else {
            return false;
        }
    }
    
    
    @Override
    public int hashCode()
    {
        return gid.hashCode();
    }        
    
    
    public WampDict toWampObject(boolean withData)
    {
        WampDict obj = new WampDict();
        obj.put("gid", getGid());
        obj.put("appId", getApplication().getAppId());
        obj.put("appName", getApplication().getName());
        obj.put("admin", (admin != null) ? admin.toString() : "" );
        obj.put("automatch", isAutoMatchEnabled());
        obj.put("hidden", isHidden());
        obj.put("num", getNumMembers());
        obj.put("min", getMinMembers());
        obj.put("max", getMaxMembers());
        obj.put("delta", getDeltaMembers());
        obj.put("avail", getAvailSlots());
        obj.put("observable", isObservableGroup());
        obj.put("dynamic", isDynamicGroup());
        obj.put("alliances", isAlliancesAllowed());
        obj.put("description", getDescription());        
        obj.put("state", String.valueOf(getState()));
        obj.put("turn", getTurn());
        obj.put("password", (password != null) && (password.length() > 0) );
        if(withData) {
            obj.put("data", data);
            obj.put("initialData", initialData);
            WampList actions = new WampList();
            for(GroupAction action : getActions()) {
                actions.add(action.toWampObject());
            }
            obj.put("actions", actions);
        }
        return obj;
    }

}

