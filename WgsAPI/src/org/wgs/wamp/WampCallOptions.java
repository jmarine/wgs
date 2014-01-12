package org.wgs.wamp;


public class WampCallOptions 
{
    public enum RunOnEnum   { all, any, partition }
    public enum RunModeEnum { gather, progressive }
    
    private int timeout;
    private String rkey;
    private RunOnEnum runOn;
    private RunModeEnum runMode;
    
    public WampCallOptions(WampDict options) 
    {
        this.timeout = 0;
        this.runOn = RunOnEnum.all;
        
        if(options != null) {
            
            if(options.has("timeout")) {
                setTimeout(options.get("timeout").asLong().intValue());
            }
            
            if(options.has("runon")) {
                setRunOn(RunOnEnum.valueOf(options.get("runon").asText()));
                if(runOn == RunOnEnum.partition) {
                    setPartition(options.get("rkey").asText());
                }
            }                 
            
            if(options.has("runmode")) {
                setRunMode(RunModeEnum.valueOf(options.get("runmode").asText()));
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
        
    
}
