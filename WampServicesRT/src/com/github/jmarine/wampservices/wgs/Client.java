package com.github.jmarine.wampservices.wgs;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.github.jmarine.wampservices.WampSocket;

/**
 *
 * @author jordi
 */
public class Client {
    
    private WampSocket  socket;
    private ConnectionState state;
    
    private User user;
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

    /**
     * @return the state
     */
    public ConnectionState getState() {
        return state;
    }

    /**
     * @param state the state to set
     */
    public void setState(ConnectionState state) {
        this.state = state;
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
