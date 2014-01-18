package org.wgs.wamp;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.persistence.EntityManager;

import org.wgs.entity.User;
import org.wgs.entity.UserId;
import org.wgs.util.Base64;
import org.wgs.util.PBKDF2;
import org.wgs.util.Storage;


@WampModuleName("wamp.cra")
public class WampCRA extends WampModule 
{
    public WampCRA(WampApplication app)
    {
        super(app);
    }
    

    @WampRPC(name="request")
    public String authRequest(WampSocket socket, String authKey, WampDict extra) throws Exception
    {
        if(socket.getState() == WampConnectionState.AUTHENTICATED) {
            throw new WampException("wamp.cra.error.already_authenticated", "already authenticated");
        }
        if(socket.getSessionData().containsKey("_clientPendingAuthInfo")) {
            throw new WampException("wamp.cra.error.authentication_already_requested", "authentication request already issues - authentication pending");
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");
        WampDict res = getAuthPermissions(authKey);
        WampDict info = new WampDict();
        info.put("authid", UUID.randomUUID().toString());
        info.put("authkey", authKey);
        info.put("timestamp", sdf.format(new Date()));
        info.put("sessionid", socket.getSessionId());
        info.put("permissions", res.get("permissions"));
        if(extra != null) info.put("extra", extra);
        if(res.has("authextra")) info.put("authextra", res.get("authextra"));
        
        String infoser = WampObject.getSerializer(WampEncoding.JSon).serialize(info).toString();
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
    
    
    @WampRPC(name="authenticate")
    public WampDict auth(WampSocket socket, String signature) throws Exception
    {
        if(socket.getState() == WampConnectionState.AUTHENTICATED) {
            throw new WampException("wamp.cra.error.already_authenticated", "already authenticated");
        }
        if(!socket.getSessionData().containsKey("_clientPendingAuthInfo")) {
            throw new WampException("wamp.cra.error.authentication_failed", "no authentication previously requested");
        }

        WampDict info = (WampDict)socket.getSessionData().remove("_clientPendingAuthInfo");;
        WampDict perms = (WampDict)socket.getSessionData().remove("_clientPendingAuthPerms");            
        String clientPendingAuthSig = (String)socket.getSessionData().remove("_clientPendingAuthSig");
        if(clientPendingAuthSig == null) clientPendingAuthSig = "";
        
        if(signature == null) {
            socket.setUserPrincipal(null);
            socket.setState(WampConnectionState.ANONYMOUS);
        } else {
            if(!signature.equals(clientPendingAuthSig)) {
                throw new WampException("wamp.cra.error.authentication_failed", "signature for authentication request is invalid");
            }

            String authKey = info.getText("authkey");

            UserId userId = new UserId(User.LOCAL_USER_DOMAIN, authKey);
            User usr = Storage.findEntity(User.class, userId);
            usr.setLastLoginTime(Calendar.getInstance());
            usr = Storage.saveEntity(usr);

            socket.setUserPrincipal(usr);
            socket.setState(WampConnectionState.AUTHENTICATED);
        }

        return perms;
    }
    

    private WampDict getAuthPermissions(String authKey) 
    {
        WampDict res = new WampDict();
        WampDict perms = new WampDict();
        WampList pubsub = new WampList();
        WampList rpcs = new WampList();
        perms.put("pubsub", pubsub);
        perms.put("rpcs", rpcs);
        res.put("permissions", perms);
        return res;
    }
    
    
    private String getAuthSecret(String authKey) throws WampException
    {
        if(authKey != null) {
            EntityManager manager = Storage.getEntityManager();
            UserId userId = new UserId(User.LOCAL_USER_DOMAIN, authKey);
            User usr = manager.find(User.class, userId);
            manager.close();
            if(usr == null) {
                throw new WampException("wamp.cra.error.no_such_authkey", authKey + " authKey doesn't exists");
            }
            return usr.getPassword();
        } else {
            return "";
        }
    }
    
    
    private String authSignature(String authChallenge, String authSecret, WampDict authExtra) throws Exception
    {
        if(authSecret == null) authSecret = "";
        byte[] derivedSecret = deriveKey(authSecret, authExtra);
        
        SecretKeySpec keyspec = new SecretKeySpec(derivedSecret, "HmacSHA256");
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(keyspec);
        
        byte[] h = hmac.doFinal(authChallenge.getBytes("UTF8"));
        return Base64.encodeByteArrayToBase64(h);
    }
    

    private byte[] deriveKey(String secret, WampDict extra) throws Exception
    {
        if(extra != null && extra.has("salt")) {
            byte[] salt = extra.getText("salt").getBytes("UTF8");
            int iterations = extra.has("iterations")? extra.getLong("iterations").intValue() : 10000;
            int keylen = extra.has("keylen")? extra.getLong("keylen").intValue() : 32;

            PBKDF2 pbkdf2 = new PBKDF2("HmacSHA256");
            //return pbkdf2.deriveKey(secret.getBytes("UTF8"), salt, iterations, keylen);
            return Base64.encodeByteArrayToBase64(pbkdf2.deriveKey(secret.getBytes("UTF8"), salt, iterations, keylen)).getBytes("UTF8");
        } else {
            return secret.getBytes("UTF8");
        }
    }    
    
}
