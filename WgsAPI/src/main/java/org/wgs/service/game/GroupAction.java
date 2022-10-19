package org.wgs.service.game;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.TemporalType;
import org.wgs.security.User;
import org.wgs.wamp.type.WampDict;


@Entity
@Table(name="APP_GROUP_ACTION")
@NamedQueries({
    @NamedQuery(name="wgs.findByGroup",query="SELECT OBJECT(a) FROM GroupAction a WHERE a.applicationGroup = :group ORDER BY a.actionTime")
})

public class GroupAction implements java.io.Serializable
{
    private static final long serialVersionUID = 0L;
    
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="gid", referencedColumnName="gid")
    private Group applicationGroup;

    @Id
    @Column(name="actionOrder", nullable=true)
    private int actionOrder;
    
    @jakarta.persistence.Temporal(TemporalType.TIMESTAMP)
    @Column(name="actionTime")
    private java.util.Calendar actionTime; 

    @Column(name="actionName")
    private String actionName;
    
    @Column(name="actionValue")
    private String actionValue;
    
    @Column(name="slot")
    private int slot;
    
    @ManyToOne(fetch= FetchType.EAGER)
    @JoinColumns({@JoinColumn(name="uid", referencedColumnName = "uid")})      
    private User user;

    
    /**
     * @return the applicationGroup
     */
    public Group getApplicationGroup() {
        return applicationGroup;
    }

    /**
     * @param applicationGroup the applicationGroup to set
     */
    public void setApplicationGroup(Group applicationGroup) {
        this.applicationGroup = applicationGroup;
    }

    
    /**
     * @return the actionTime
     */
    public java.util.Calendar getActionTime() {
        return actionTime;
    }

    
    /**
     * @param actionTime the actionTime to set
     */
    public void setActionTime(java.util.Calendar actionTime) {
        this.actionTime = actionTime;
    }
    
    /**
     * @return the actionOrder
     */
    public int getActionOrder() {
        return actionOrder;
    }

    /**
     * @param actionOrder the actionOrder to set
     */
    public void setActionOrder(int actionOrder) {
        this.actionOrder = actionOrder;
    }

    /**
     * @return the actionName
     */
    public String getActionName() {
        return actionName;
    }

    /**
     * @param actionName the actionName to set
     */
    public void setActionName(String actionName) {
        this.actionName = actionName;
    }


    /**
     * @return the actionValue
     */
    public String getActionValue() {
        return actionValue;
    }

    /**
     * @param actionValue the actionValue to set
     */
    public void setActionValue(String actionValue) {
        this.actionValue = actionValue;
    }

    /**
     * @return the user
     */
    public User getUser() {
        return user;
    }


    
    /**
     * @param user the user to set
     */
    public void setUser(User user) {
        this.user = user;
    }

    
    /**
     * @param slot the slot to set
     */
    public void setSlot(int slot) {
        this.slot = slot;
    }    
    
    
    /**
     * @return the slot
     */
    public int getSlot() {
        return slot;
    }
    

    public WampDict toWampObject() 
    {
        WampDict event = new WampDict();

        //event.put("order", actionOrder);
        event.put("type",  actionName);
        event.put("value", actionValue);
        event.put("time",  actionTime);
        if(slot >= 0) event.put("slot",  slot);
        else if(user != null) event.put("user", user.getName());
        
        return event;
    }

}
