package org.wampservices.wgs;

import org.wampservices.entity.User;
import org.wampservices.WampConnectionState;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.wampservices.WampSocket;

/**
 *
 * @author jordi
 */
public class Client {
    
    private WampSocket  socket;
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
        return (User)this.socket.getPrincipal();
    }
    
    public void setUser(User user)
    {
        this.socket.setPrincipal(user);
        if(user != null) {
            socket.setState(WampConnectionState.AUTHENTICATED);
        }
        else if(socket.getState() != WampConnectionState.OFFLINE) {
            socket.setState(WampConnectionState.ANONYMOUS);
        }
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
