package com.github.jmarine.wampservices.wsg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import com.github.jmarine.wampservices.WampTopic;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;


public class Group 
{
    private GroupState state;
    private int  minMembers;
    private int  maxMembers;
    private int  deltaMembers;
    private String adminNick;

    private boolean autoMatchEnabled;
    private boolean autoMatchCompleted;
    private boolean dynamicGroup;
    private boolean alliancesAllowed;
    private boolean observableGroup;
    private boolean hidden;

    private String gid;
    private String description;
    private String data;
    private String password;

    private ArrayList<Member> members = new ArrayList<Member>();

    private Application app;

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
     * @return the adminNick
     */
    public String getAdminNick() {
        return adminNick;
    }

    /**
     * @param adminNick the adminNick to set
     */
    public void setAdminNick(String adminNick) {
        this.adminNick = adminNick;
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
     * @return the app
     */
    public Application getApplication() {
        return app;
    }

    /**
     * @param app the app to set
     */
    public void setApplication(Application app) {
        this.app = app;
    }
    
    
    
    public int getNumMembers()  
    {
        int count = 0;
        for(int i = 0; i < members.size(); i++) {
            Member member = getMember(i);
            if( (member != null) && (member.getClient() != null)) count++;
        }
        return count;
    }
    
    
    public int getAvailSlots() 
    {
        if(maxMembers <= 0) {
            return -1;
        } else {
            int count = getNumMembers();
            return maxMembers - count;
        }
    }
    
    
    public int getNumSlots() 
    {
        return members.size();
    }

    
    
    Member getMember(int index) 
    {
        if(index < getNumSlots()) {
            return members.get(index);
        } else {
            return null;
        }
    }

    void setMember(int index, Member member) 
    {
        while(index >= members.size()) {
            members.add(null);
        }
        members.set(index, member);
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
    
    public ObjectNode toJSON()
    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode obj = mapper.createObjectNode();
        obj.put("gid", getGid());
        obj.put("admin", getAdminNick());
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
        return obj;
    }

}

