package org.wgs.security;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.wgs.util.Base64;
import org.wgs.util.PBKDF2;
import org.wgs.util.Storage;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampException;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.type.WampConnectionState;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampObject;



public class WampCRA 
{
    public  static final String WAMP_AUTH_ID_PROPERTY_NAME = "__wamp_authid";

    public static WampDict getChallenge(WampSocket socket, String authId) throws Exception
    {
        if(socket.getState() == WampConnectionState.AUTHENTICATED) {
            throw new WampException(null, "wamp.cra.error.already_authenticated", null, null);
        }
        if(socket.containsSessionData("_clientPendingAuthInfo")) {
            throw new WampException(null, "wamp.cra.error.authentication_already_requested", null, null);
        }
        
        User usr = UserRepository.findUserByLoginAndDomain(authId, socket.getRealm());
        if(authId != null && usr == null) {
            System.out.println("wamp.cra.error.no_such_authid: authid doesn't exists: " + authId);
            throw new WampException(null, "wamp.cra.error.no_such_authid", null, null);
        }
        
        WampDict passwordSaltParams = WampCRA.getPasswordSaltParams(usr);
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss:SSSX");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        WampDict info = new WampDict();
        info.put("authid", authId);
        info.put("authrole", usr.isAdministrator()? "admin" : "user");
        info.put("authmethod", "wampcra");
        info.put("authprovider", socket.getAuthProvider());
        info.put("nonce", UUID.randomUUID().toString());
        info.put("timestamp", sdf.format(new Date()));
        info.put("session", socket.getSessionId());
        
        String infoser = WampEncoding.JSON.getSerializer().serialize(info).toString();
        String authSecret = getAuthSecret(usr);
        String sig = "";
        if(authId != null && authId.length() > 0) {
            sig = authSignature(infoser, authSecret, passwordSaltParams);
        } else {
            infoser = "";
        }
        
        socket.putSessionData("_clientPendingAuthInfo", info);
        socket.putSessionData("_clientPendingAuthSig", sig);
        
        WampDict challenge = new WampDict();
        challenge.put("challenge", infoser);
        if(passwordSaltParams != null) challenge.putAll(passwordSaltParams);
        return challenge;
    }
    
    
    public static void verifySignature(WampApplication app, WampSocket socket, String signature) throws Exception
    {
        if(socket.getState() == WampConnectionState.AUTHENTICATED) {
            throw new WampException(null, "wamp.cra.error.already_authenticated", null, null);
        }
        if(!socket.containsSessionData("_clientPendingAuthInfo")) {
            System.out.println("wamp.cra.error.authentication_failed: no authentication previously requested");
            throw new WampException(null, "wamp.cra.error.authentication_failed", null, null);
        }

        WampDict info = (WampDict)socket.removeSessionData("_clientPendingAuthInfo");

        String clientPendingAuthSig = (String)socket.removeSessionData("_clientPendingAuthSig");
        if(clientPendingAuthSig == null) clientPendingAuthSig = "";
        
        if(signature == null) {
            app.onUserLogon(socket, null, WampConnectionState.ANONYMOUS);
        } else {
            if(!signature.equals(clientPendingAuthSig)) {
                System.out.println("wamp.cra.error.authentication_failed: signature for authentication request is invalid");
                throw new WampException(null, "wamp.cra.error.authentication_failed", null, null);
            }

            String authId = info.getText("authid");
            User usr = UserRepository.findUserByLoginAndDomain(authId, socket.getRealm());
            usr.setLastLoginTime(Calendar.getInstance());
            usr = Storage.saveEntity(usr);

            app.onUserLogon(socket, usr, WampConnectionState.AUTHENTICATED);
        }

    }
    
    
    private static WampDict getPasswordSaltParams(User usr) 
    {
        // simulate password salt
        WampDict saltParams = new WampDict();
        saltParams.put("salt", String.valueOf(WampProtocol.newGlobalScopeId()));
        saltParams.put("keylen", 32);
        saltParams.put("iterations", 1000);
        return saltParams;
    }    
    

    private static String getAuthSecret(User usr) throws WampException
    {
        if(usr != null) {
            return usr.getPassword();
        } else {
            return "";
        }
    }
    
    
    public static String authSignature(String authChallenge, String authSecret, WampDict extra) throws Exception
    {
        if(authSecret == null) authSecret = "";
        byte[] derivedSecret = deriveKey(authSecret, extra);
        
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
