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

    public GroupFilter(WampDict node) 
    {
        this.appId = null;
        this.state = null;        
        this.scope = Scope.mine;
        if (node != null) {
            if(node.has("appId")) this.appId = node.getText("appId");
            if(node.has("scope")) this.scope = Scope.valueOf(node.getText("scope"));
            if(node.has("state")) this.state = GroupState.valueOf(node.getText("state"));
        }
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
            ejbql = ejbql + ", IN(g.members) m ";
            if(where.length() > 0) where.append(" AND ");
            if(scope == Scope.mine) {
                where.append("m.user = :user");
            } else {  
                // scope == Scope.friends
                // IMPORTANT: IT REQUIRES EclipseLink >= 2.4.0
                where.append("(m.user = :user OR m.user.id IN (SELECT f.id FROM User t, IN(t.friends) f where t = :user))");
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
