package org.wgs.wamp.rpc;

import java.util.HashSet;
import java.util.Set;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


public class WampCallOptions 
{
    public enum RunOnEnum   { any, all, partition }
    public enum RunModeEnum { progressive, gather }
    
    private int timeout;
    private String rkey;
    private RunOnEnum runOn;
    private RunModeEnum runMode;
    private boolean discloseMe;
    private Set<Long> excluded;
    private Set<Long> eligible;
    private boolean   excludeMe;   

    private Long   callerId;
    private String authId;
    private String authProvider;
    private String authRole;
    
    
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
                setEligible((WampList)options.get("eligible"));
            }                   
            
            if(options.has("exclude")) {
                setExcluded((WampList)options.get("exclude"));
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
    public Set<Long> getExcluded() {
        return excluded;
    }

    /**
     * @param excluded the excluded to set
     */
    public void setExcluded(Set<Long> excluded) {
        this.excluded = excluded;
    }
    
    /**
     * @param excluded the excluded to set
     */
    private void setExcluded(WampList excluded) {
        this.excluded = new HashSet<Long>();
        for(int i = 0; i < excluded.size(); i++) {
            this.excluded.add(excluded.getLong(i));
        }
    }    

    /**
     * @return the eligible
     */
    public Set<Long> getEligible() {
        return eligible;
    }

    /**
     * @param eligible the eligible to set
     */
    public void setEligible(Set<Long> eligible) {
        this.eligible = eligible;
    }
    
    
    /**
     * @param eligible the eligible to set
     */
    private void setEligible(WampList eligible) {
        this.eligible = new HashSet<Long>();
        for(int i = 0; i < eligible.size(); i++) {
            this.eligible.add(eligible.getLong(i));
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
 
        if(eligible != null) {
            WampList eligibleList = new WampList();
            eligibleList.addAll(eligible.toArray());
            options.put("eligible", eligibleList);
        }
        
        if(excluded != null) {
            WampList excludedList = new WampList();
            excludedList.addAll(excluded.toArray());
            options.put("exclude", excludedList);
        }    
        
        return options;
    }    
    
        
}
