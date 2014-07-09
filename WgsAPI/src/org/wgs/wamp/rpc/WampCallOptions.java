package org.wgs.wamp.rpc;

import org.wgs.wamp.type.WampDict;


public class WampCallOptions 
{
    public enum RunOnEnum   { any, all, partition }
    public enum RunModeEnum { progressive, gather }
    
    private int timeout;
    private String rkey;
    private RunOnEnum runOn;
    private RunModeEnum runMode;
    private boolean discloseMe;

    private Long   callerId;
    private String authId;
    private String authProvider;
    private String authRole;
    
    
    public WampCallOptions(WampDict options) 
    {
        this.timeout = 0;
        this.discloseMe = false;
        this.runOn = RunOnEnum.all;
        this.runMode = RunModeEnum.gather;
        
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
        
}
