package org.wgs.core;

import java.util.HashSet;
import java.util.List;

import org.wgs.entity.User;
import org.wgs.wamp.WampDict;
import org.wgs.wamp.WampList;
import org.wgs.wamp.WampObject;
import org.wgs.wamp.WampSubscription;
import org.wgs.wamp.WampSubscriptionOptions;


public class GroupFilter extends WampSubscriptionOptions 
{
    public enum Scope { mine, friends, all };
    
    private Module module;
    private Scope  scope;
    private HashSet<String> subscriptions;

    public GroupFilter(Module module, WampDict node) 
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
    public boolean isEligibleForEvent(Long sid, WampSubscription subscription, WampList payload, WampDict payloadKw) 
    {
        if (scope == Scope.all) {
            return true;
        }
        
        WampDict dict = payloadKw;
        String gid = (dict.has("gid")) ? dict.get("gid").asText() : null;
        if (gid != null) {
            String cmd = dict.has("cmd")? dict.get("cmd").asText() : "";
            boolean wasSubscribed = subscriptions.contains(gid);
            if(cmd.equals("group_deleted")) subscriptions.remove(gid);
            
            if(wasSubscribed) {
                return true;
            } else {
                Group  group = module.getGroup(gid);
                if(group == null) {
                    return false;
                } else if(sid != null) {
                    Client client = module.getClient(sid);
                    return subscribeGroup(group, client);
                }
            }
        }
        return false;
    }
    
}
