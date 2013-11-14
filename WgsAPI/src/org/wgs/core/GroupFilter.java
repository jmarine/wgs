/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wgs.core;

import java.util.HashSet;
import java.util.List;
import org.codehaus.jackson.JsonNode;
import org.wgs.entity.User;
import org.wgs.wamp.WampSubscription;
import org.wgs.wamp.WampSubscriptionOptions;

/**
 *
 * @author jordi
 */
public class GroupFilter extends WampSubscriptionOptions 
{
    public enum Scope { mine, friends, all };
    
    private Module module;
    private Scope  scope;
    private HashSet<String> subscriptions;

    public GroupFilter(Module module, JsonNode node) 
    {
        super(node);
        this.subscriptions = new HashSet<String>();
        this.module = module;
        this.scope = Scope.all;
        if (node != null) {
            scope = Scope.valueOf(node.get("scope").asText());
        }
    }

    
    public boolean subscribeGroup(Group group, Client client) 
    {
        if (scope == Scope.all) {
            return true;
        }
                
        User user = client.getUser();
        String userId = (user != null) ? user.getFQid() : "";
        List<User> friends = (scope == Scope.friends) ? client.getUser().getFriends() : null;
        for (Member member : group.getMembers()) {
            User memberUser = member.getUser();
            if(memberUser != null) {
                String memberFQid = memberUser.getFQid();
                if(userId.equals(memberFQid)) {
                    subscriptions.add(group.getGid());
                    return true;
                } else if (friends != null) {
                    for (User friend : friends) {
                        if(memberFQid.equals(friend.getFQid())) {
                            subscriptions.add(group.getGid());
                            return true;
                        }
                    }
                }
            }
        }
        
        return false;
    }
    
    
    @Override
    public void updateOptions(WampSubscriptionOptions options)
    {
        super.updateOptions(options);
        if(options != null && options instanceof GroupFilter) {
            this.scope = ((GroupFilter)options).scope;
            this.subscriptions = new HashSet<String>();
        }
    }
    
    
    @Override
    public boolean isEligibleForEvent(WampSubscription subscription, JsonNode event) 
    {
        if (scope == Scope.all) {
            return true;
        }
        
        String gid = event.has("gid") ? event.get("gid").asText() : null;
        if (gid != null) {
            String cmd = event.has("cmd")? event.get("cmd").asText() : "";
            boolean wasSubscribed = subscriptions.contains(gid);
            if(cmd.equals("group_deleted")) subscriptions.remove(gid);
            
            if(wasSubscribed) {
                return true;
            } else {
                Group  group = module.getGroup(gid);
                if(group == null) {
                    return false;
                } else {
                    Client client = module.getClient(subscription.getSocket().getSessionId());
                    return subscribeGroup(group, client);
                }
            }
        }
        return false;
    }
    
}
