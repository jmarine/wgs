package org.wgs.wamp;

import java.util.List;


public class WampCallOptions 
{
    public enum PartitionModeEnum { all, any }
    
    private int timeout;
    private WampList partitionKeys;
    private PartitionModeEnum partitionMode;
    
    public WampCallOptions(WampDict options) 
    {
        this.timeout = 0;
        this.partitionMode = PartitionModeEnum.all;
        
        if(options != null) {
            if(options.has("TIMEOUT")) {
                setTimeout(options.get("TIMEOUT").asInt());
            }
            
            if(options.has("PKEYS")) {
                setPartitionKeys((WampList)options.get("PKEYS"));
            }
            
            if(options.has("PMODE")) {
                setPartitionMode(PartitionModeEnum.valueOf(options.get("PMODE").asText()));
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
     * @return the partitionKeys
     */
    public WampList getPartitionKeys() {
        return partitionKeys;
    }

    /**
     * @param partitionKeys the partitionKeys to set
     */
    public void setPartitionKeys(WampList partitionKeys) {
        this.partitionKeys = partitionKeys;
    }

    /**
     * @return the partitionMode
     */
    public PartitionModeEnum getPartitionMode() {
        return partitionMode;
    }

    /**
     * @param partitionMode the partitionMode to set
     */
    public void setPartitionMode(PartitionModeEnum partitionMode) {
        this.partitionMode = partitionMode;
    }
    
}
