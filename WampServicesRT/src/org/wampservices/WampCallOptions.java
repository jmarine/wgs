package org.wampservices;

import java.util.List;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;


public class WampCallOptions 
{

    public enum PartitionModeEnum { all, any }
    
    private int timeout;
    private ArrayNode partitionKeys;
    private PartitionModeEnum partitionMode;
    
    public WampCallOptions(ObjectNode options) 
    {
        this.timeout = 0;
        this.partitionMode = PartitionModeEnum.all;
        
        if(options != null) {
            if(options.has("TIMEOUT")) {
                setTimeout(options.get("TIMEOUT").asInt());
            }
            
            if(options.has("PKEYS")) {
                setPartitionKeys((ArrayNode)options.get("PKEYS"));
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
    public ArrayNode getPartitionKeys() {
        return partitionKeys;
    }

    /**
     * @param partitionKeys the partitionKeys to set
     */
    public void setPartitionKeys(ArrayNode partitionKeys) {
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
