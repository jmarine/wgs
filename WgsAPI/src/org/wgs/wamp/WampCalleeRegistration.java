package org.wgs.wamp;

import java.util.Collection;
import java.util.HashMap;


public class WampCalleeRegistration
{
    private Long registrationId;
    
    private MatchEnum matchType;
    
    private String methodRegExp;
    
    private HashMap<Long,WampRemoteMethod> remoteMethods = new HashMap<Long,WampRemoteMethod>();
    
    
    
    public WampCalleeRegistration(Long registrationId, MatchEnum matchType, String methodUriOrPattern)
    {
        this.registrationId = registrationId;
        this.matchType = matchType;
        this.methodRegExp = WampServices.getPatternRegExp(matchType, methodUriOrPattern);
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
