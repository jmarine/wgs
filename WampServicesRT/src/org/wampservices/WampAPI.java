/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wampservices;

import java.security.spec.KeySpec;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.persistence.EntityManager;
import org.codehaus.jackson.node.ObjectNode;
import org.wampservices.entity.User;
import org.wampservices.entity.UserId;
import org.wampservices.util.Storage;

/**
 *
 * @author jordi
 */
public class WampAPI extends WampModule 
{
    public WampAPI(WampApplication app)
    {
        super(app);
    }
    
    @Override
    public String getBaseURL() {
        return "https://api.wamp.ws/procedure#";
    }
    

    @WampRPC(name="authreq")
    public String authRequest(String authKey, ObjectNode extra)
    {
        String challenge = "";
        return challenge;
    }
    
    
    @WampRPC(name="auth")
    public ObjectNode auth(String signature)
    {
        ObjectNode permissions = null;
        return permissions;
    }
    
    
    private String getAuthSecret(String authKey) 
    {
        EntityManager manager = Storage.getEntityManager();
        UserId userId = new UserId(User.LOCAL_USER_DOMAIN, authKey);
        User usr = manager.find(User.class, userId);
        return usr.getPassword();
    }
    
    
    
}
