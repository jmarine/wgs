package org.wgs.wamp.rpc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.type.WampMatchType;


public class WampCalleeRegistration
{
    private Long registrationId;
    
    private WampMatchType matchType;
    
    private String methodRegExp;
    
    private HashMap<String, HashMap<Long,WampRemoteMethod>> remoteMethods = new HashMap<String, HashMap<Long,WampRemoteMethod>>();
    
    
    
    public WampCalleeRegistration(Long registrationId, WampMatchType matchType, String methodUriOrPattern)
    {
        this.registrationId = registrationId;
        this.matchType = matchType;
        this.methodRegExp = WampBroker.getPatternRegExp(matchType, methodUriOrPattern);
    }
    
    
    public Long getId()
    {
        return this.registrationId;
    }    
    
    public String getRegExp()
    {
        return methodRegExp;
    }
    

    
    
    
    public synchronized void addRemoteMethod(Long sessionId, WampRemoteMethod remoteMethod)
    {
        String realm = remoteMethod.getRemotePeer().getRealm();
        HashMap<Long,WampRemoteMethod> realmMethods = remoteMethods.get(realm);
        if(realmMethods == null) {
            realmMethods = new HashMap<Long,WampRemoteMethod>();
            remoteMethods.put(realm, realmMethods);
        }
        realmMethods.put(sessionId, remoteMethod);                    
    }
    
    public synchronized void removeRemoteMethod(WampSocket socket)
    {
        String realm = socket.getRealm();
        HashMap<Long,WampRemoteMethod> realmMethods = remoteMethods.get(realm);
        if(realmMethods != null) {
            realmMethods.remove(socket.getSessionId());
            if(realmMethods.size() == 0) remoteMethods.remove(realm);
        }
    }
    
    public synchronized int getRemoteMethodsCount()
    {
        return remoteMethods.size();
    }
        
    
    public Collection<WampRemoteMethod> getRemoteMethods(String realm)
    {
        HashMap<Long,WampRemoteMethod> realmMethods = remoteMethods.get(realm);
        if(realmMethods != null) {
            return remoteMethods.get(realm).values();
        } else {
            return Collections.<WampRemoteMethod>emptyList();
        }
    }

    
    public Collection<Long> selectRemotePeers(String realm, WampCallOptions callOptions)
    {
        HashMap<Long,WampRemoteMethod> realmMethods = remoteMethods.get(realm);
        if(realmMethods != null) {
            return realmMethods.keySet();
        } else {
            return Collections.<Long>emptyList();
        }
    }
    
}
