package org.wgs.security;

import java.util.List;
import org.wgs.util.Storage;


public class UserRepository 
{
    public static User findUserByLoginAndDomain(String login, String domain) 
    {
        User usr = null;
        if (login != null) {
            List<User> found = Storage.findEntities(User.class, "wgs.findUsersByLoginAndDomain", login, domain);
            usr = (found != null && found.size() > 0) ? found.get(0) : null;
        }
        return usr;
    }
    
}
