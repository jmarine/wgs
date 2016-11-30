package org.wgs.wamp.rpc;

import java.util.HashSet;
import java.util.Set;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


public class WampCallOptions 
{
    public enum RunOnEnum   { any, all, partition }
    public enum RunModeEnum { progressive, gather }
    
    private int         timeout;
    private String      rkey;
    private RunOnEnum   runOn;
    private RunModeEnum runMode;
    private boolean     discloseMe;
    private Set<Long>   excludedSessionIds;
    private Set<String> excludedAuthIds;
    private Set<String> excludedAuthRoles;
    private Set<Long>   eligibleSessionIds;
    private Set<String> eligibleAuthIds;
    private Set<String> eligibleAuthRoles;  
    private boolean     excludeMe;   

    private Long        callerId;
    private String      authId;
    private String      authProvider;
    private String      authRole;
    
    
    public WampCallOptions(WampDict options) 
    {
        this.timeout = 0;
        this.discloseMe = false;
        this.runOn = RunOnEnum.any;
        this.runMode = RunModeEnum.gather;
        setExcludeMe(true); // a Caller of a procedure will never itself be forwarded the call issued
        
        if(options != null) {
            
            if(options.has("timeout")) {
                setTimeout(options.getLong("timeout").intValue());
            }
            
            if(options.has("runon")) {
                setRunOn(RunOnEnum.valueOf(options.getText("runon").toLowerCase()));
                if(runOn == RunOnEnum.partition) {
                    setPartition(options.getText("rkey"));
                }
            }                 
            
            if(options.has("receive_progress")) {
                setRunMode(RunModeEnum.progressive);
            }
            
            if(options.has("runmode")) {
                setRunMode(RunModeEnum.valueOf(options.getText("runmode").toLowerCase()));
            }
            
            if(options.has("disclose_me")) {
                setDiscloseMe(options.getBoolean("disclose_me"));
            }     
            
            if(options.has("authid")) {
                setAuthId(options.getText("authid"));
                setDiscloseMe(true);
            }                     

            if(options.has("authprovider")) {
                setAuthProvider(options.getText("authprovider"));
            }         

            if(options.has("authrole")) {
                setAuthRole(options.getText("authrole"));
            }     
            
            if(options.has("caller")) {
                setCallerId(options.getLong("caller"));
            }       
            
            if(options.has("exclude_me")) {
                setExcludeMe(options.getBoolean("exclude_me"));
            }     
            
            if(options.has("eligible")) {
                setEligibleSessionIds((WampList)options.get("eligible"));
            }                   
            
            if(options.has("eligible_authid")) {
                setEligibleAuthIds((WampList)options.get("eligible_authid"));
            }                        
            
            if(options.has("eligible_authrole")) {
                setEligibleAuthRoles((WampList)options.get("eligible_authrole"));
            }                        
            
            if(options.has("exclude")) {
                setExcludedSessionIds((WampList)options.get("exclude"));
            }            
            
            if(options.has("exclude_authid")) {
                setExcludedAuthIds((WampList)options.get("exclude_authid"));
            }                        
            
            if(options.has("exclude_authrole")) {
                setExcludedAuthRoles((WampList)options.get("exclude_authrole"));
            }                              
            
        }
    }

    
    /**
     * @return the timeout
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * @param timeout the timeout to set
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * @return the rkey
     */
    public String getPartition() {
        return rkey;
    }

    /**
     * @param rkey the rkey to set
     */
    public void setPartition(String rkey) {
        this.rkey = rkey;
    }

    /**
     * @return the runon call option
     */
    public RunOnEnum getRunOn() {
        return runOn;
    }

    /**
     * @param runon the runon to set
     */
    public void setRunOn(RunOnEnum runon) {
        this.runOn = runon;
    }
    
    /**
     * @return the runMode call option
     */
    public RunModeEnum getRunMode() {
        return runMode;
    }

    /**
     * @param runMode the runMode to set
     */
    public void setRunMode(RunModeEnum runMode) {
        this.runMode = runMode;
    }
    
    public void setDiscloseMe(boolean discloseMe)
    {
        this.discloseMe = discloseMe;
    }
    
    public boolean hasDiscloseMe()
    {
        return discloseMe;
    }
        
    public String getAuthId()
    {
        return authId;
    }
    
    private void setAuthId(String authId)
    {
        this.authId = authId;
    }
    
    public String getAuthProvider()
    {
        return authProvider;
    }
    
    private void setAuthProvider(String authProvider)
    {
        this.authProvider = authProvider;
    }
    
    public String getAuthRole()
    {
        return authRole;
    }
    
    private void setAuthRole(String authRole)
    {
        this.authRole = authRole;
    }

    public Long getCallerId()
    {
        return callerId;
    }
    
    private void setCallerId(Long callerId)
    {
        this.callerId = callerId;
    }
    
    /**
     * @return the excludeMe
     */
    public boolean hasExcludeMe() {
        return excludeMe;
    }

    /**
     * @param excludeMe the excludeMe to set
     */
    public void setExcludeMe(boolean excludeMe) {
        this.excludeMe = excludeMe;
    }

    /**
     * @return the excluded
     */
    public Set<Long> getExcludedSessionIds() {
        return excludedSessionIds;
    }

    /**
     * @param excluded the excluded to set
     */
    public void setExcludedSessionIds(Set<Long> excluded) {
        this.excludedSessionIds = excluded;
    }
    
    /**
     * @param excluded the excluded to set
     */
    private void setExcludedSessionIds(WampList excluded) {
        this.excludedSessionIds = new HashSet<Long>();
        for(int i = 0; i < excluded.size(); i++) {
            this.excludedSessionIds.add(excluded.getLong(i));
        }
    }    
    
    
    /**
     * @return the excluded
     */
    public Set<String> getExcludedAuthIds() {
        return excludedAuthIds;
    }

    /**
     * @param excluded the excluded to set
     */
    public void setExcludedAuthIds(Set<String> excluded) {
        this.excludedAuthIds = excluded;
    }
    
    /**
     * @param excluded the excluded to set
     */
    private void setExcludedAuthIds(WampList excluded) {
        this.excludedAuthIds = new HashSet<String>();
        for(int i = 0; i < excluded.size(); i++) {
            this.excludedAuthIds.add(excluded.getText(i));
        }
    }        
    
    
    /**
     * @return the excluded
     */
    public Set<String> getExcludedAuthRoles() {
        return excludedAuthRoles;
    }

    /**
     * @param excluded the excluded to set
     */
    public void setExcludedAuthRoles(Set<String> excluded) {
        this.excludedAuthRoles = excluded;
    }
    
    /**
     * @param excluded the excluded to set
     */
    private void setExcludedAuthRoles(WampList excluded) {
        this.excludedAuthRoles = new HashSet<String>();
        for(int i = 0; i < excluded.size(); i++) {
            this.excludedAuthRoles.add(excluded.getText(i));
        }
    }      
    

    /**
     * @return the eligible
     */
    public Set<Long> getEligibleSessionIds() {
        return eligibleSessionIds;
    }

    /**
     * @param eligible the eligible to set
     */
    public void setEligibleSessionIds(Set<Long> eligible) {
        this.eligibleSessionIds = eligible;
    }
    
    
    /**
     * @param eligible the eligible to set
     */
    private void setEligibleSessionIds(WampList eligible) {
        this.eligibleSessionIds = new HashSet<Long>();
        for(int i = 0; i < eligible.size(); i++) {
            this.eligibleSessionIds.add(eligible.getLong(i));
        }
    }  
    
    
    /**
     * @return the eligible
     */
    public Set<String> getEligibleAuthIds() {
        return eligibleAuthIds;
    }

    /**
     * @param eligible the eligible to set
     */
    public void setEligibleAuthIds(Set<String> eligible) {
        this.eligibleAuthIds = eligible;
    }
    
    
    /**
     * @param eligible the eligible to set
     */
    private void setEligibleAuthIds(WampList eligible) {
        this.eligibleAuthIds = new HashSet<String>();
        for(int i = 0; i < eligible.size(); i++) {
            this.eligibleAuthIds.add(eligible.getText(i));
        }
    }        


    /**
     * @return the eligible
     */
    public Set<String> getEligibleAuthRoles() {
        return eligibleAuthRoles;
    }

    /**
     * @param eligible the eligible to set
     */
    public void setEligibleAuthRoles(Set<String> eligible) {
        this.eligibleAuthRoles = eligible;
    }
    
    
    /**
     * @param eligible the eligible to set
     */
    private void setEligibleAuthRoles(WampList eligible) {
        this.eligibleAuthRoles = new HashSet<String>();
        for(int i = 0; i < eligible.size(); i++) {
            this.eligibleAuthRoles.add(eligible.getText(i));
        }
    }         
    
    
    public WampDict toWampObject()
    {
        WampDict options = new WampDict();
        if(discloseMe) options.put("disclose_me", discloseMe);
        if(!excludeMe) options.put("exclude_me", excludeMe);
        if(timeout > 0) options.put("timeout", timeout);
        if(runOn != null) options.put("runon", runOn.toString());
        if(runMode != null && runMode != RunModeEnum.gather) options.put("runmode", runMode.toString());
        if(rkey != null) options.put("rkey", rkey);
 
        if(eligibleSessionIds != null) {
            WampList eligibleList = new WampList();
            eligibleList.addAll(eligibleSessionIds.toArray());
            options.put("eligible", eligibleList);
        }
        
        if(eligibleAuthIds != null) {
            WampList eligibleList = new WampList();
            eligibleList.addAll(eligibleAuthIds.toArray());
            options.put("eligible_authid", eligibleList);
        }        
        
        if(eligibleAuthRoles != null) {
            WampList eligibleList = new WampList();
            eligibleList.addAll(eligibleAuthRoles.toArray());
            options.put("eligible_authrole", eligibleList);
        }       
        
        if(excludedSessionIds != null) {
            WampList excludedList = new WampList();
            excludedList.addAll(excludedSessionIds.toArray());
            options.put("exclude", excludedList);
        }    
        
        if(excludedAuthIds != null) {
            WampList excludedList = new WampList();
            excludedList.addAll(excludedAuthIds.toArray());
            options.put("exclude_authid", excludedList);
        }            
        
        if(excludedAuthRoles != null) {
            WampList excludedList = new WampList();
            excludedList.addAll(excludedAuthRoles.toArray());
            options.put("exclude_authrole", excludedList);
        } 
        
        return options;
    }    
    
        
}
