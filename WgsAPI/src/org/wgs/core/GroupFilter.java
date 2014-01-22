package org.wgs.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.wgs.entity.User;
import org.wgs.util.Storage;
import org.wgs.wamp.WampDict;
import org.wgs.wamp.WampList;
import org.wgs.wamp.WampObject;
import org.wgs.wamp.WampSubscription;
import org.wgs.wamp.WampSubscriptionOptions;


public class GroupFilter
{
    public enum Scope { mine, friends, all };
    
    private String appId;
    private Scope  scope;
    private GroupState state;

    public GroupFilter(String appId, Scope scope, GroupState state) 
    {
        this.appId = appId;
        this.scope = scope;
        this.state = state;        
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
    
    public List<Group> getGroups(Client client)
    {
        HashMap<String,Object> params = new HashMap<String,Object>();        
        
        String ejbql = "SELECT OBJECT(g) FROM AppGroup g";
        StringBuilder where = new StringBuilder();
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
        if(scope != null && scope != Scope.all) {
            
            if(where.length() > 0) where.append(" AND ");
            if(scope == Scope.mine) {
                ejbql = ejbql + ", IN(g.members) m ";
                where.append("m.user = :user");
            } else {  
                // scope == Scope.friends
                // IMPORTANT: IT REQUIRES EclipseLink >= 2.4.0
                where.append("g.gid IN (SELECT g.gid FROM AppGroup g2, IN(g2.members) m WHERE m.user = :user OR EXISTS(SELECT f.id FROM User t, IN(t.friends) f WHERE t = :user AND m.user = f))");
            }
            params.put("user", client.getUser());
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
