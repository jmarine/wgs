package org.wgs.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.wgs.entity.User;
import org.wgs.entity.UserId;
import org.wgs.util.Storage;
import org.wgs.wamp.types.WampDict;
import org.wgs.wamp.types.WampList;
import org.wgs.wamp.types.WampObject;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampSubscriptionOptions;


public class GroupFilter
{
    public enum Scope { mine, all };
    
    private String appId;
    private GroupState state;
    private Scope  scope;
    private User user;

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
        String ejbql = "SELECT OBJECT(g) FROM AppGroup g";
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
        
        if(scope == Scope.mine && user != null) {
            ejbql = ejbql + ", IN(g.members) m";
            if(where.length() > 0) where.append(" AND ");
            where.append("m.user = :user");
            params.put("user", user);
        }
        
        if(where.length() > 0) {
            ejbql = ejbql + " WHERE " + where.toString();
        }
        
        EntityManager manager = Storage.getEntityManager();
        TypedQuery<Group> query = manager.createQuery(ejbql, Group.class);        
        for(String key : params.keySet()) 
        {
            query.setParameter(key, params.get(key));
        }
        
        List<Group> groups = query.getResultList();
        manager.close();

        return groups;
    }
    
}
