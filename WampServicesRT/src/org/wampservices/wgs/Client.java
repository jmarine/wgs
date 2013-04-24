package org.wampservices.wgs;

import java.security.Principal;
import org.wampservices.entity.User;
import org.wampservices.WampConnectionState;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.persistence.EntityManager;
import org.wampservices.WampSocket;
import org.wampservices.entity.UserId;
import org.wampservices.util.Storage;

/**
 *
 * @author jordi
 */
public class Client {
    
    private User user;
    private WampSocket socket;
    private Map<String,Group> groups = new ConcurrentHashMap<String,Group>();


    public String getSessionId() {
        return socket.getSessionId();
    }
    
    /**
     * @return the client
     */
    public WampSocket getSocket() {
        return socket;
    }

    /**
     * @param client the client to set
     */
    public void setSocket(WampSocket socket) {
        this.socket = socket;
    }


    public User getUser()
    {
        User user = null;
        Principal principal = socket.getUserPrincipal();
        if(principal != null) {
            if(principal instanceof User) {
                user = (User)principal;
            } else {
                String principalName = principal.getName();
                if(this.user == null) {
                    EntityManager manager = Storage.getEntityManager();
                    UserId userId = new UserId(User.LOCAL_USER_DOMAIN, principalName);
                    this.user = manager.find(User.class, userId);
                    manager.close();                    
                }
                user = this.user;
            }
        }
        return user;
    }
    
    
    /**
     * @return the groups
     */
    public Map<String,Group> getGroups() {
        return groups;
    }

    /**
     * @param groups the groups to set
     */
    public void addGroup(Group group) {
        groups.put(group.getGid(), group);
    }
    
    public void removeGroup(Group group)
    {
        groups.remove(group.getGid());
    }
    
    

}
