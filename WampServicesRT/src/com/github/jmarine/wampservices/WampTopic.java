/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.jmarine.wampservices;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author jordi
 */
public class WampTopic {
    private String uri;
    private Map<String,WampSocket> sockets = new ConcurrentHashMap<String,WampSocket>();

    /**
     * @return the name
     */
    public String getURI() {
        return uri;
    }

    /**
     * @param name the name to set
     */
    public void setURI(String uri) {
        this.uri = uri;
    }
    
    public String getBaseURI() {
        int pos = uri.indexOf("#");
        if(pos == -1)  return uri;
        else return uri.substring(0, pos+1);
    }

    /**
     * @return the sockets
     */
    public void addSocket(WampSocket socket) {
        sockets.put(socket.getSessionId(), socket);
    }

    /**
     * @param socket the sockets to set
     */
    public void removeSocket(WampSocket socket) {
        sockets.remove(socket.getSessionId());
    }
    
    public WampSocket getSocket(String sid)
    {
        return sockets.get(sid);
    }

    public Set<String> getSocketIds()
    {
        return sockets.keySet();
    }  
    
    public int getSocketCount()
    {
        return sockets.size();
    }    

}
