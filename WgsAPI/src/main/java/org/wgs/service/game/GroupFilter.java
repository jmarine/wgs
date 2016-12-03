package org.wgs.service.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.wgs.security.User;
import org.wgs.util.Storage;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampObject;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampSubscriptionOptions;


public class GroupFilter implements AutoCloseable
{
    public enum Scope { mine, all };
    
    private String appId;
    private GroupState state;
    private Scope  scope;
    private User user;
    
    private EntityManager manager;
    

    public GroupFilter(String appId, GroupState state, Scope scope, User user) 
    {
        this.appId = appId;
        this.state = state;        
        this.scope = scope;
        this.user = user;
    }
    
    /**
     * @param appId the appId to set
     */
    public void setAppId(String appId) {
        this.appId = appId;
    }

    /**
     * @param scope the scope to set
     */
    public void setScope(Scope scope) {
        this.scope = scope;
    }

    /**
     * @param state the state to set
     */
    public void setState(GroupState state) {
        this.state = state;
    }    
    
    public List<Group> getGroups()
    {
        if(user == null) return new ArrayList<Group>();
        
        String ejbql = "SELECT DISTINCT OBJECT(g) FROM AppGroup g";
        StringBuilder where = new StringBuilder();
        HashMap<String,Object> params = new HashMap<String,Object>();        
        
        if(appId != null) {
            if(where.length() > 0) where.append(" AND ");
            where.append("g.application.id = :appId");
            params.put("appId", appId);
        }
        
        if(state != null) {
            if(where.length() > 0) where.append(" AND ");
            where.append("g.state = :state");
            params.put("state", state);
        }      
        
        if(scope == null || scope == Scope.mine) {
            ejbql = ejbql + ", IN(g.members) m";
            if(where.length() > 0) where.append(" AND ");
            where.append("m.user = :user and m.state <> org.wgs.service.game.MemberState.DELETED");
            params.put("user", user);
        }
        
        if(where.length() > 0) {
            ejbql = ejbql + " WHERE " + where.toString();
        }
        
        manager = Storage.getEntityManager();
        TypedQuery<Group> query = manager.createQuery(ejbql, Group.class);        
        for(Entry<String,Object> entry : params.entrySet()) 
        {
            query.setParameter(entry.getKey(), entry.getValue());
        }
        
        List<Group> groups = query.getResultList();

        return groups;
    }
    

    @Override
    public void close() throws Exception {
        if(manager != null) {
            manager.close();
        }
    }    
    
}
