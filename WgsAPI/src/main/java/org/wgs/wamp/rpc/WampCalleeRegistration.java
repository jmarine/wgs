package org.wgs.wamp.rpc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
    
    private ConcurrentHashMap<Long,WampRemoteMethod> remoteMethodsBySID = new ConcurrentHashMap<Long,WampRemoteMethod>();
    
    

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
    
    
    public void addRemoteMethod(WampSocket socket, WampRemoteMethod remoteMethod)
    {
        Long sessionId = socket.getWampSessionId();
        if(sessionId == null) {
            sessionId = socket.getSocketId();
        }
        remoteMethodsBySID.put(sessionId, remoteMethod);                    
    }
    
    public WampRemoteMethod removeRemoteMethod(WampSocket socket)
    {
        Long sessionId = socket.getWampSessionId();
        if(sessionId == null) {
            sessionId = socket.getSocketId();
        }        
        return remoteMethodsBySID.remove(sessionId);
    }
    
    public int getRemoteMethodsCount()
    {
        return remoteMethodsBySID.size();
    }
        
    
    public Collection<WampRemoteMethod> getRemoteMethods(Long callerId, WampCallOptions optionsParam)
    {
        Collection<WampRemoteMethod> retval = new ArrayList<WampRemoteMethod>();

        final WampCallOptions options = (optionsParam != null)? optionsParam : new WampCallOptions(null);
        Set<Long> eligibleOption = options.getEligibleSessionIds();
        Set<Long> eligibleCopy = (eligibleOption != null) ? new HashSet<Long>(eligibleOption) : null;
        if(eligibleCopy == null) eligibleCopy = new HashSet<Long>(remoteMethodsBySID.keySet());
        else eligibleCopy.retainAll(remoteMethodsBySID.keySet());

        Set<Long> excludedCopy = options.getExcludedSessionIds();
        if(excludedCopy == null) excludedCopy = new HashSet<Long>();  
        else excludedCopy = new HashSet<Long>(excludedCopy);
        if(callerId != null && options.hasExcludeMe()) {
            excludedCopy.add(callerId);
        }
        
        final Set<Long> excludedFinal = excludedCopy;
        List<Long> sids = java.util.Arrays.asList(eligibleCopy.parallelStream().filter(sid -> {
                WampRemoteMethod method = remoteMethodsBySID.get(sid);
                if(method == null) return false;

                WampSocket socket = method.getRemotePeer();

                if(socket == null) return false;
                if(excludedFinal != null && excludedFinal.contains(sid)) {
                    return false;
                }

                String fqAuthId = socket.getAuthId()+"@"+socket.getAuthProvider();
                if(options.getExcludedAuthIds() != null) {
                    if(options.getExcludedAuthIds().contains(fqAuthId)) return false;
                }
                if(options.getEligibleAuthIds() != null) {
                    if(!options.getEligibleAuthIds().contains(fqAuthId)) return false;
                }

                if(options.getExcludedAuthRoles() != null) {
                    for(String role : options.getExcludedAuthRoles()) {
                        if(socket.hasAuthRole(role)) return false;
                    }
                }                
                if(options.getEligibleAuthRoles() != null) {
                    boolean hasEligibleRole = false;
                    for(String role : options.getEligibleAuthRoles()) {
                        if(socket.hasAuthRole(role)) {
                            hasEligibleRole = true;
                            break;
                        }
                    }
                    if(!hasEligibleRole) return false;
                }

                return true;
            }).toArray(Long[]::new));
        
        for (Long sid : sids) {
            WampRemoteMethod method = remoteMethodsBySID.get(sid);
            if(method != null) {
                retval.add(method);
            }   
        }
        
        return retval;
    }
    
}
