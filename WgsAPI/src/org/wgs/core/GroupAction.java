package org.wgs.core;

import org.wgs.entity.User;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.TemporalType;


@Entity
@Table(name="APP_GROUP_ACTION")
@NamedQueries({
    @NamedQuery(name="wgs.findByGroup",query="SELECT OBJECT(a) FROM GroupAction a WHERE a.applicationGroup = :group ORDER BY a.actionTime")
})

public class GroupAction implements java.io.Serializable
{
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="gid", referencedColumnName="gid")
    private Group applicationGroup;

    @Id
    @javax.persistence.Temporal(TemporalType.TIMESTAMP)
    @Column(name="actionTime")
    private java.util.Calendar actionTime; 
    
    @Column(name="actionOrder", nullable=true)
    private int actionOrder;

    @Column(name="actionName")
    private String actionName;
    
    @Column(name="actionType")
    private String actionType;
    
    @Column(name="actionValue")
    private String actionValue;
    
    @ManyToOne(fetch= FetchType.EAGER)
    @JoinColumns({
        @JoinColumn(name="member_gid", referencedColumnName = "gid"),
        @JoinColumn(name="member_slot", referencedColumnName = "slot")
    })      
    private Member member;    
    
    @ManyToOne(fetch= FetchType.EAGER)
    @JoinColumns({
        @JoinColumn(name="uid", referencedColumnName = "uid"),
        @JoinColumn(name="oic_provider", referencedColumnName = "oic_provider")
    })      
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
     * @return the actionType
     */
    public String getActionType() {
        return actionType;
    }

    /**
     * @param actionType the actionType to set
     */
    public void setActionType(String actionType) {
        this.actionType = actionType;
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
     * @param member the member to set
     */
    public void setMember(Member member) {
        this.member = member;
    }    
    
    
    /**
     * @param user the user to set
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * @return the member
     */
    public Member getMember() {
        return member;
    }
    
}
