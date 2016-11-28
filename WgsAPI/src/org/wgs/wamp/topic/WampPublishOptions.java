package org.wgs.wamp.topic;

import java.util.HashSet;
import java.util.Set;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampObject;


public class WampPublishOptions 
{
    private boolean     discloseMe;
    private boolean     excludeMe;
    private Set<Long>   excludedSessionIds;
    private Set<String> excludedAuthIds;
    private Set<String> excludedAuthRoles;
    private Set<Long>   eligibleSessionIds;
    private Set<String> eligibleAuthIds;
    private Set<String> eligibleAuthRoles;    
    private boolean     ack;
        
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
                setEligibleSessionIds((WampList)node.get("eligible"));
            }      
            
            if(node.has("eligible_authid")) {
                setEligibleAuthIds((WampList)node.get("eligible_authid"));
            }   
            
            if(node.has("eligible_authrole")) {
                setEligibleAuthRoles((WampList)node.get("eligible_authrole"));
            }                     
            
            if(node.has("exclude")) {
                setExcludedSessionIds((WampList)node.get("exclude"));
            }
            
            if(node.has("exclude_authid")) {
                setExcludedAuthIds((WampList)node.get("exclude_authid"));
            }
            
            if(node.has("exclude_authrole")) {
                setExcludedAuthRoles((WampList)node.get("exclude_authrole"));
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

