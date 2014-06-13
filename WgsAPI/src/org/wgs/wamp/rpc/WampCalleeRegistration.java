package org.wgs.wamp.rpc;

import java.util.Collection;
import java.util.HashMap;
import org.wgs.wamp.type.WampMatchType;
import org.wgs.wamp.topic.WampBroker;


public class WampCalleeRegistration
{
    private Long registrationId;
    
    private WampMatchType matchType;
    
    private String methodRegExp;
    
    private HashMap<Long,WampRemoteMethod> remoteMethods = new HashMap<Long,WampRemoteMethod>();
    
    
    
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
    

    
    
    
    public void addRemoteMethod(Long sessionId, WampRemoteMethod remoteMethod)
    {
        
        remoteMethods.put(sessionId, remoteMethod);
    }
    
    public void removeRemoteMethod(Long sessionId)
    {
        remoteMethods.remove(sessionId);
    }
    
    public Collection<WampRemoteMethod> getRemoteMethods()
    {
        return remoteMethods.values();
    }
    
    
    public Collection<Long> selectRemotePeers(WampCallOptions callOptions)
    {
        return remoteMethods.keySet();
    }
    
}
