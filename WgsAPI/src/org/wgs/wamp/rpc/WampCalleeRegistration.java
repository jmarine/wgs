package org.wgs.wamp.rpc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.type.WampMatchType;


public class WampCalleeRegistration
{
    private Long registrationId;
    
    private String realmName;
    
    private WampMatchType matchType;
    
    private String methodRegExp;
    
    private ConcurrentHashMap<Long,WampRemoteMethod> remoteMethods = new ConcurrentHashMap<Long,WampRemoteMethod>();
    
    
    
    public WampCalleeRegistration(String realmName, Long registrationId, WampMatchType matchType, String methodUriOrPattern)
    {
        this.realmName = realmName;
        this.registrationId = registrationId;
        this.matchType = matchType;
        this.methodRegExp = WampBroker.getPatternRegExp(matchType, methodUriOrPattern);
    }
    
    
    public Long getId()
    {
        return this.registrationId;
    }    
    
    public String getRealmName()
    {
        return realmName;
    }
    
    
    public String getRegExp()
    {
        return methodRegExp;
    }
    
    
    public WampMatchType getMatchType()
    {
        return matchType;
    }
    
    
    public void addRemoteMethod(Long sessionId, WampRemoteMethod remoteMethod)
    {
        remoteMethods.put(sessionId, remoteMethod);                    
    }
    
    public WampRemoteMethod removeRemoteMethod(WampSocket socket)
    {
        return remoteMethods.remove(socket.getSessionId());
    }
    
    public int getRemoteMethodsCount()
    {
        return remoteMethods.size();
    }
        
    
    public Collection<WampRemoteMethod> getRemoteMethods(Long callerId, WampCallOptions options)
    {
        Collection<WampRemoteMethod> retval = new ArrayList<WampRemoteMethod>();

        if(options == null) options = new WampCallOptions(null);
        Set<Long> eligibleParam = options.getEligible();
        Set<Long> excluded = options.getExcluded();
        Set<Long> eligible = (eligibleParam != null) ? new HashSet<Long>(eligibleParam) : null;
        if(eligible == null) eligible = remoteMethods.keySet();
        else eligible.retainAll(remoteMethods.keySet());

        if(excluded == null) excluded = new HashSet<Long>();        
        if(options == null || (options.hasExcludeMe() && callerId != null) ) {
            excluded.add(callerId);
        }

        for (Long sid : eligible) {
            if(!excluded.contains(sid)) {
                WampRemoteMethod method = remoteMethods.get(sid);
                if(method != null) {
                    retval.add(method);
                }
            }
        }
        
        return retval;
    }
    
}
