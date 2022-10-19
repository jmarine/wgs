package org.wgs.security;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import org.wgs.security.User;
import org.wgs.security.User;
import org.wgs.security.User;


@Entity
@Table(name="USR_PUSH_CHANNEL")
@NamedQueries({
    @NamedQuery(name="UserPushChannel.findByAppAndUser",query="SELECT OBJECT(c) FROM UserPushChannel c WHERE c.appClientName = ?1 and c.user = ?2")
})
public class UserPushChannel implements Serializable
{
    private static final long serialVersionUID = 0L;    
    
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
