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
        return "https://api.wamp.ws/procedure#";
    }
    

    @WampRPC(name="authreq")
    public String authRequest(WampSocket socket, String authKey, ObjectNode extra)
    {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode info = mapper.createObjectNode();
        info.put("authid", UUID.randomUUID().toString());
        info.put("authkey", authKey);
        info.put("timestamp", sdf.format(new Date()));
        info.put("sessionid", socket.getSessionId());
        if(extra != null) info.put("extra", extra);

/*
            res = getAuthPermissions(authKey)
            
            if res is None:
               res = {'permissions': {}}
               res['permissions'] = {'pubsub': [], 'rpc': []}
            info['permissions'] = res['permissions']
            if 'authextra' in res:
                info['authextra'] = res['authextra']

            if authKey:
               ## authenticated session
               ##
               infoser = self.factory._serialize(info)
               sig = self.authSignature(infoser, authSecret)

               self._clientPendingAuth = (info, sig, res)
               return infoser
            else:
               ## anonymous session
               ##
               self._clientPendingAuth = (info, None, res)
               return None
*/                       
        
        String challenge = "";
        return challenge;
    }
    
    
    @WampRPC(name="auth")
    public ObjectNode auth(WampSocket socket, String signature)
    {
        ObjectNode permissions = null;
        return permissions;
/*
       if self._clientAuthenticated:
         raise Exception(self.shrink(WampProtocol.URI_WAMP_ERROR + "already-authenticated"), "already authenticated")
      if self._clientPendingAuth is None:
         raise Exception(self.shrink(WampProtocol.URI_WAMP_ERROR + "no-authentication-requested"), "no authentication previously requested")

      ## check signature
      ##
      if type(signature) not in [str, unicode, types.NoneType]:
         raise Exception(self.shrink(WampProtocol.URI_WAMP_ERROR + "invalid-argument"), "signature must be a string or None (was %s)" % str(type(signature)))
      if self._clientPendingAuth[1] != signature:
         ## delete pending authentication, so that no retries are possible. authid is only valid for 1 try!!
         ## FIXME: drop the connection?
         self._clientPendingAuth = None
         raise Exception(self.shrink(WampProtocol.URI_WAMP_ERROR + "invalid-signature"), "signature for authentication request is invalid")

      ## at this point, the client has successfully authenticated!

      ## get the permissions we determined earlier
      ##
      perms = self._clientPendingAuth[2]
      ## delete auth request and mark client as authenticated
      ##
      authKey = self._clientPendingAuth[0]['authkey']
      self._clientAuthenticated = True
      self._clientPendingAuth = None
      if self._clientAuthTimeoutCall is not None:
         self._clientAuthTimeoutCall.cancel()
         self._clientAuthTimeoutCall = None

      ## fire authentication callback
      ##
      self.onAuthenticated(authKey, perms)

      ## return permissions to client
      ##
      return perms['permissions']
 */        
    }
    
    
    private String getAuthSecret(String authKey) 
    {
        EntityManager manager = Storage.getEntityManager();
        UserId userId = new UserId(User.LOCAL_USER_DOMAIN, authKey);
        User usr = manager.find(User.class, userId);
        return usr.getPassword();
    }
    
    private byte[] deriveKey(String secret, ObjectNode extra) throws Exception
    {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] PWHash = md5.digest(secret.getBytes("UTF8"));            
        
        if(extra != null && extra.has("salt")) {
            byte[] salt = Base64.decodeBase64ToByteArray(extra.get("salt").asText());
            int iterations = extra.get("iterations").asInt(10000);
            int keylen = extra.get("keylen").asInt(32);

            PBKDF2 pbkdf2 = new PBKDF2("HmacSHA256");
            byte[] result = pbkdf2.deriveKey(PWHash, salt, iterations, keylen);
            return result;
        } else {
            return PWHash;
        }
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
    
}
