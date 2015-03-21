package org.wgs.security;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import org.wgs.security.User;


@Entity
@Table(name="USR_PUSH_CHANNEL")
@NamedQueries({
    @NamedQuery(name="UserPushChannel.findByAppAndUser",query="SELECT OBJECT(c) FROM UserPushChannel c WHERE c.appClientName = ?1 and c.user = ?2")
})
public class UserPushChannel 
{
    @Id
    @Column(name="app_client_name")    
    private String appClientName;

    @Id
    @ManyToOne
    @JoinColumns({
        @JoinColumn(name="uid", referencedColumnName = "uid")
    })      
    private User user;
    
    @Column(name="notification_channel", length=1024)
    private String notificationChannel;

    

    /**
     * @return the appClientName
     */
    public String getAppClientName() {
        return appClientName;
    }

    /**
     * @param appClientName the appClientName to set
     */
    public void setAppClientName(String appClientName) {
        this.appClientName = appClientName;
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
     * @return the notificationChannel
     */
    public String getNotificationChannel() {
        return notificationChannel;
    }

    /**
     * @param notificationChannel the notificationChannel to set
     */
    public void setNotificationChannel(String notificationChannel) {
        this.notificationChannel = notificationChannel;
    }
    
    
}
