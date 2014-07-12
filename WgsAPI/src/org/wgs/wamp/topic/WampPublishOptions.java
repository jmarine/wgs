package org.wgs.wamp.topic;

import java.util.HashSet;
import java.util.Set;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampObject;


public class WampPublishOptions 
{
    private boolean   excludeMe;
    private Set<Long> excluded;
    private Set<Long> eligible;
    private boolean   discloseMe;
    private boolean   ack;
        
    public WampPublishOptions() { 
        init(null);
    }
    
    public WampPublishOptions(WampDict node) { 
        init(node);
    }
    
    public void init(WampDict node) 
    {
        setAck(false);      // By default, no acknowledgement
        setExcludeMe(true); // By default, a Publisher of an event will not itself receive an event published
        
        if(node != null) {
            if(node.has("exclude_me")) {
                setExcludeMe(node.getBoolean("exclude_me"));
            }     
            
            if(node.has("disclose_me")) {
                setDiscloseMe(node.getBoolean("disclose_me"));
            }
            
            if(node.has("discloseme")) {
                setDiscloseMe(node.getBoolean("discloseme"));
            }            
            
            if(node.has("eligible")) {
                setEligible((WampList)node.get("eligible"));
            }                   
            
            if(node.has("exclude")) {
                setExcluded((WampList)node.get("exclude"));
            }
            
            if(node.has("acknowledgement")) {
                setAck(node.getBoolean("acknowledgement"));
            }
            
            if(node.has("acknowledge")) {
                setAck(node.getBoolean("acknowledge"));
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

    /**
     * @return the identifyMe
     */
    public boolean hasDiscloseMe() {
        return discloseMe;
    }

    /**
     * @param identifyMe the identifyMe to set
     */
    public void setDiscloseMe(boolean identifyMe) {
        this.discloseMe = identifyMe;
    }
    

    /**
     * @return the ack
     */
    public boolean hasAck() {
        return ack;
    }

    /**
     * @param ack the ack to set
     */
    public void setAck(boolean ack) {
        this.ack = ack;
    }    
    
    
    public WampDict toWampObject()
    {
        WampDict options = new WampDict();
        if(ack) options.put("acknowledge", ack);
        if(discloseMe) options.put("disclose_me", discloseMe);
        if(!excludeMe) options.put("exclude_me", excludeMe);
 
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

