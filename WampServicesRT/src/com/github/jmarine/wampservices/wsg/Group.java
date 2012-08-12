
package com.github.jmarine.wampservices.wsg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import com.github.jmarine.wampservices.WampTopic;


public class Group {
    private GroupState state;
    private int  minMembers;
    private int  maxMembers;
    private String adminNick;

    private boolean autoMatchEnabled;
    private boolean autoMatchCompleted;
    private boolean dynamicGroup;
    private boolean alliancesAllowed;
    private boolean observableGroup;

    private String gid;
    private String description;
    private String data;

    private ArrayList<GroupMember> members = new ArrayList<GroupMember>();

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
            GroupMember member = getMember(i);
            if(member != null) count++;
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

    
    
    GroupMember getMember(int index) 
    {
        if(index < getNumSlots()) {
            return members.get(index);
        } else {
            return null;
        }
    }

    void setMember(int index, GroupMember member) 
    {
        while(index >= members.size()) {
            members.add(null);
        }
        members.set(index, member);
    }
    
    
}


