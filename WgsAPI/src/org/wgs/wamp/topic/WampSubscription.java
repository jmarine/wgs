
package org.wgs.wamp.topic;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.wgs.util.RefCount;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.type.WampMatchType;


public class WampSubscription 
{
    private Long subscriptionId;
    
    private String topicRegExp;
    
    private WampSubscriptionOptions options;

    private HashMap<Long, RefCount<WampSocket>> sockets = new HashMap<Long, RefCount<WampSocket>>();
    private HashMap<String, HashSet<Long>> sessionIdsByRealm = new HashMap<String, HashSet<Long>>();
    
    private Collection<WampTopic> topics = null;
    
    
    public WampSubscription(Long subscriptionId, WampMatchType matchType, String topicUriOrPattern, Collection<WampTopic> topics, WampSubscriptionOptions options) 
    {
        this.subscriptionId = subscriptionId;
        this.topicRegExp = WampBroker.getPatternRegExp(matchType, topicUriOrPattern);        
        this.topics  = topics;
        this.options = (options != null)? options : new WampSubscriptionOptions(null);
    }
    
    
    public Long getId()
    {
        return subscriptionId;
    }

    public Collection<WampTopic>  getTopics()
    {
        return topics;
    }

    
    public synchronized boolean addSocket(WampSocket socket)
    {
        String realm = socket.getRealm();
        HashSet<Long> realmSessions = sessionIdsByRealm.get(realm);
        if(realmSessions == null) {
            realmSessions = new HashSet<Long>();
            sessionIdsByRealm.put(realm, realmSessions);
        }
        
        RefCount<WampSocket> ref = sockets.get(socket.getWampSessionId());
        if(ref == null) {
            ref = new RefCount<WampSocket>(socket, 1);
            sockets.put(socket.getWampSessionId(), ref);
            realmSessions.add(socket.getWampSessionId());
            return true;
        } else {
            ref.refCount(+1);
            return false;
        }
    }
    
    public synchronized WampSocket removeSocket(Long sessionId)
    {
        RefCount<WampSocket> ref = sockets.get(sessionId);
        if(ref == null) {
            return null;
        } else {
            if(ref.refCount(-1) == 0) {
                WampSocket socket = ref.getObject();
                sockets.remove(sessionId);
                HashSet<Long> realmSessions = sessionIdsByRealm.get(socket.getRealm());
                if(realmSessions != null) realmSessions.remove(sessionId);
                else System.out.println("WARN: no sessionId " + sessionId + " in realm " + socket.getRealm());
            }
            return ref.getObject();
        }
    }
    
    public WampSocket getSocket(Long sessionId)
    {
        RefCount<WampSocket> refCount = sockets.get(sessionId);
        if(refCount != null) {
            return refCount.getObject();
        } else {
            return null;
        }
    }
    

    
    public int getSocketsCount()
    {
        return sockets.size();
    }
    
    public synchronized Set<Long> getSessionIds(String realm)
    {
        if(realm == null) {
            return sockets.keySet();
        } else {
            Set<Long> set = sessionIdsByRealm.get(realm);
            if(set == null) set = new HashSet<Long>();
            else set = new HashSet<Long>(set);
            return set;
        }
    }
    
    public String getTopicRegExp() 
    {
        return topicRegExp;
    }
    
    public WampSubscriptionOptions getOptions()
    {
        return options;
    }
    
}
