package org.wampservices;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.persistence.EntityManager;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.wampservices.entity.User;
import org.wampservices.entity.UserId;
import org.wampservices.util.Base64;
import org.wampservices.util.PBKDF2;
import org.wampservices.util.Storage;


public class WampAPI extends WampModule 
{
    public WampAPI(WampApplication app)
    {
        super(app);
    }
    
    @Override
    public String getBaseURL() {
        return "http://api.wamp.ws/procedure#";
    }
    

    @WampRPC(name="authreq")
    public String authRequest(WampSocket socket, String authKey, ObjectNode extra) throws Exception
    {
        if(socket.getState() == WampConnectionState.AUTHENTICATED) {
            throw new WampException(WampApplication.WAMP_ERROR_URI + "already-authenticated", "already authenticated");
        }
        if(socket.getSessionData().containsKey("_clientPendingAuthInfo")) {
            throw new WampException(WampApplication.WAMP_ERROR_URI +  "authentication-already-requested", "authentication request already issues - authentication pending");
        }
        
        ObjectNode res = getAuthPermissions(authKey);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode info = mapper.createObjectNode();
        info.put("authid", UUID.randomUUID().toString());
        info.put("authkey", authKey);
        info.put("timestamp", sdf.format(new Date()));
        info.put("sessionid", socket.getSessionId());
        info.put("permissions", (ObjectNode)res.get("permissions"));
        if(extra != null) info.put("extra", extra);
        if(res.has("authextra")) info.put("authextra", res.get("authextra"));
        
        String infoser = info.toString();
        String authSecret = this.getAuthSecret(authKey);
        String sig = "";
        if(authKey != null && authKey.length() > 0) {
            sig = this.authSignature(infoser, authSecret, extra);
        } else {
            infoser = "";
        }
        
        socket.getSessionData().put("_clientPendingAuthInfo", info);
        socket.getSessionData().put("_clientPendingAuthSig", sig);
        socket.getSessionData().put("_clientPendingAuthPerms", res);
        
        return infoser;
    }
    
    
    @WampRPC(name="auth")
    public ObjectNode auth(WampSocket socket, String signature) throws Exception
    {
        if(socket.getState() == WampConnectionState.AUTHENTICATED) {
            throw new WampException(WampApplication.WAMP_ERROR_URI + "already-authenticated", "already authenticated");
        }
        if(!socket.getSessionData().containsKey("_clientPendingAuthInfo")) {
            throw new WampException(WampApplication.WAMP_ERROR_URI + "no-authentication-requested", "no authentication previously requested");
        }

        ObjectNode info = (ObjectNode)socket.getSessionData().remove("_clientPendingAuthInfo");;
        ObjectNode perms = (ObjectNode)socket.getSessionData().remove("_clientPendingAuthPerms");            
        String clientPendingAuthSig = (String)socket.getSessionData().remove("_clientPendingAuthSig");
        if(clientPendingAuthSig == null) clientPendingAuthSig = "";
        
        if(!signature.equals(clientPendingAuthSig)) {
            throw new WampException(WampApplication.WAMP_ERROR_URI + "invalid-signature", "signature for authentication request is invalid");
        }
        
        String authKey = info.get("authkey").asText();
        
        EntityManager manager = Storage.getEntityManager();
        UserId userId = new UserId(User.LOCAL_USER_DOMAIN, authKey);
        User usr = manager.find(User.class, userId);
        manager.close();
        
        socket.setUserPrincipal(usr);
        socket.setState(WampConnectionState.AUTHENTICATED);

        return perms;
    }
    

    private ObjectNode getAuthPermissions(String authKey) 
    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode res = mapper.createObjectNode();
        ObjectNode perms = mapper.createObjectNode();
        ArrayNode pubsub = mapper.createArrayNode();
        ArrayNode rpcs = mapper.createArrayNode();
        perms.put("pubsub", pubsub);
        perms.put("rpcs", rpcs);
        res.put("permissions", perms);
        return res;
    }
    
    
    private String getAuthSecret(String authKey) 
    {
        EntityManager manager = Storage.getEntityManager();
        UserId userId = new UserId(User.LOCAL_USER_DOMAIN, authKey);
        User usr = manager.find(User.class, userId);
        manager.close();
        return usr.getPassword();
    }
    
    
    private String authSignature(String authChallenge, String authSecret, ObjectNode authExtra) throws Exception
    {
        if(authSecret == null) authSecret = "";
        byte[] derivedSecret = deriveKey(authSecret, authExtra);
        
        SecretKeySpec keyspec = new SecretKeySpec(derivedSecret, "HmacSHA256");
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(keyspec);
        
        byte[] h = hmac.doFinal(authChallenge.getBytes("UTF8"));
        return Base64.encodeByteArrayToBase64(h);
    }
    

    private byte[] deriveKey(String secret, ObjectNode extra) throws Exception
    {
        if(extra != null && extra.has("salt")) {
            byte[] salt = extra.get("salt").asText().getBytes("UTF8");
            int iterations = extra.get("iterations").asInt(10000);
            int keylen = extra.get("keylen").asInt(32);

            PBKDF2 pbkdf2 = new PBKDF2("HmacSHA256");
            //return pbkdf2.deriveKey(secret.getBytes("UTF8"), salt, iterations, keylen);
            return Base64.encodeByteArrayToBase64(pbkdf2.deriveKey(secret.getBytes("UTF8"), salt, iterations, keylen)).getBytes("UTF8");
        } else {
            return secret.getBytes("UTF8");
        }
    }    
    
}
