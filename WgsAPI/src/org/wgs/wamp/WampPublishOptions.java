package org.wgs.wamp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;


public class WampPublishOptions 
{
    private boolean     excludeMe;
    private Set<String> excluded;
    private Set<String> eligible;
    private boolean     identifyMe;
        
    public WampPublishOptions() { }
    public WampPublishOptions(JsonNode node) { 
        init(node);
    }
    
    public void init(JsonNode node) {
        
        if(node != null) {
            if(node.has("EXCLUDE_ME")) {
                setExcludeMe(node.get("EXCLUDE_ME").asBoolean());
            }     
            
            if(node.has("IDENTIFY_ME")) {
                setIdentifyMe(node.get("IDENTIFY_ME").asBoolean());
            }                 
        }
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
    public Set<String> getExcluded() {
        return excluded;
    }

    /**
     * @param excluded the excluded to set
     */
    public void setExcluded(Set<String> excluded) {
        this.excluded = excluded;
    }

    /**
     * @return the eligible
     */
    public Set<String> getEligible() {
        return eligible;
    }

    /**
     * @param eligible the eligible to set
     */
    public void setEligible(Set<String> eligible) {
        this.eligible = eligible;
    }

    /**
     * @return the identifyMe
     */
    public boolean hasIdentifyMe() {
        return identifyMe;
    }

    /**
     * @param identifyMe the identifyMe to set
     */
    public void setIdentifyMe(boolean identifyMe) {
        this.identifyMe = identifyMe;
    }
    
    
}

