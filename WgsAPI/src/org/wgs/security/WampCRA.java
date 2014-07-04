package org.wgs.security;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.wgs.util.Base64;
import org.wgs.util.PBKDF2;
import org.wgs.util.Storage;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.type.WampConnectionState;
import org.wgs.wamp.WampException;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampObject;
import org.wgs.wamp.type.WampList;



public class WampCRA 
{
    public  static final String WAMP_AUTH_ID_PROPERTY_NAME = "__wamp_authid";

    
    public static String getChallenge(WampSocket socket, String authKey, WampDict extra) throws Exception
    {
        if(socket.getState() == WampConnectionState.AUTHENTICATED) {
            throw new WampException(null, "wamp.cra.error.already_authenticated", null, null);
        }
        if(socket.getSessionData().containsKey("_clientPendingAuthInfo")) {
            throw new WampException(null, "wamp.cra.error.authentication_already_requested", null, null);
        }
        
        User usr = UserRepository.findUserByLoginAndDomain(authKey, socket.getRealm());
        if(authKey != null && usr == null) {
            System.out.println("wamp.cra.error.no_such_authkey: authKey doesn't exists: " + authKey);
            throw new WampException(null, "wamp.cra.error.no_such_authkey", null, null);
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
        String authSecret = getAuthSecret(usr);
        String sig = "";
        if(authKey != null && authKey.length() > 0) {
            sig = authSignature(infoser, authSecret, extra);
        } else {
            infoser = "";
        }
        
        socket.getSessionData().put("_clientPendingAuthInfo", info);
        socket.getSessionData().put("_clientPendingAuthSig", sig);
        socket.getSessionData().put("_clientPendingAuthPerms", res);
        
        return infoser;
    }
    
    

    
    public static WampDict verifySignature(WampApplication app, WampSocket socket, String signature) throws Exception
    {
        if(socket.getState() == WampConnectionState.AUTHENTICATED) {
            throw new WampException(null, "wamp.cra.error.already_authenticated", null, null);
        }
        if(!socket.getSessionData().containsKey("_clientPendingAuthInfo")) {
            System.out.println("wamp.cra.error.authentication_failed: no authentication previously requested");
            throw new WampException(null, "wamp.cra.error.authentication_failed", null, null);
        }

        WampDict info = (WampDict)socket.getSessionData().remove("_clientPendingAuthInfo");;
        WampDict perms = (WampDict)socket.getSessionData().remove("_clientPendingAuthPerms");            
        String clientPendingAuthSig = (String)socket.getSessionData().remove("_clientPendingAuthSig");
        if(clientPendingAuthSig == null) clientPendingAuthSig = "";
        
        if(signature == null) {
            app.onUserLogon(socket, null, WampConnectionState.ANONYMOUS);
        } else {
            if(!signature.equals(clientPendingAuthSig)) {
                System.out.println("wamp.cra.error.authentication_failed: signature for authentication request is invalid");
                throw new WampException(null, "wamp.cra.error.authentication_failed", null, null);
            }

            String authKey = info.getText("authkey");
            User usr = UserRepository.findUserByLoginAndDomain(authKey, socket.getRealm());
            usr.setLastLoginTime(Calendar.getInstance());
            usr = Storage.saveEntity(usr);

            app.onUserLogon(socket, usr, WampConnectionState.AUTHENTICATED);
        }

        return perms;
    }
    

    private static WampDict getAuthPermissions(String authKey) 
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
    
    
    private static String getAuthSecret(User usr) throws WampException
    {
        if(usr != null) {
            return usr.getPassword();
        } else {
            return "";
        }
    }
    
    
    public static String authSignature(String authChallenge, String authSecret, WampDict authExtra) throws Exception
    {
        if(authSecret == null) authSecret = "";
        byte[] derivedSecret = deriveKey(authSecret, authExtra);
        
        SecretKeySpec keyspec = new SecretKeySpec(derivedSecret, "HmacSHA256");
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(keyspec);
        
        byte[] h = hmac.doFinal(authChallenge.getBytes("UTF8"));
        return Base64.encodeByteArrayToBase64(h);
    }
    

    private static byte[] deriveKey(String secret, WampDict extra) throws Exception
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
