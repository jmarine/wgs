package org.wgs.wamp.rpc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.type.WampMatchType;


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
    
    public void removeRemoteMethod(WampSocket socket)
    {
        remoteMethods.remove(socket.getSessionId());
    }
    
    public int getRemoteMethodsCount()
    {
        return remoteMethods.size();
    }
        
    
    public Collection<WampRemoteMethod> getRemoteMethods(Long callerId, WampCallOptions options)
    {
        Collection<WampRemoteMethod> retval = new ArrayList<WampRemoteMethod>();

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
                retval.add(remoteMethods.get(sid));
            }
        }
        
        return retval;
    }
    
}
