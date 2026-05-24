package org.wgs.util;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushAsyncService;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.jose4j.lang.JoseException;
import org.apache.http.HttpResponse;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;

import org.wgs.security.NotificationService;
import org.wgs.wamp.type.WampDict;

/**
 *
 * @author jordi
 */
public class VAPID 
{
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }    
    }
    
    
    public static final String getNotificationServicePublicKeyForVAPID(String appName) throws Exception
    {
        String publicKeyBase64 = null;
        NotificationService ns = getOrCreateNotificationForVAPID(appName);
        if(ns != null) {
            publicKeyBase64 = Base64.encodeByteArrayToBase64(ns.getPublicKey()).replace("+", "-").replace("/", "_");  
        }
        return publicKeyBase64;
    }    
    
    
    public static final NotificationService getOrCreateNotificationForVAPID(String appName) throws Exception
    {
        NotificationService ns = Storage.findEntity(NotificationService.class, appName);
        if(ns == null) {

            ECNamedCurveParameterSpec parameterSpec = ECNamedCurveTable.getParameterSpec("prime256v1");
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("ECDH", "BC");
            keyPairGenerator.initialize(parameterSpec);
            KeyPair serverKey = keyPairGenerator.generateKeyPair();

            ECPrivateKey privateKey = (ECPrivateKey)serverKey.getPrivate();
            ECPublicKey publicKey = (ECPublicKey)serverKey.getPublic();

            byte[] publicKeyByteArray = Utils.encode(publicKey);
            byte[] privateKeyByteArray = Utils.encode(privateKey);

            
            ns = new NotificationService();
            ns.setAppClientName(appName);
            ns.setPublicKey(publicKeyByteArray);
            ns.setPrivateKey(privateKeyByteArray);
            Storage.createEntity(ns);
        }
        return ns;
    }
    
    
    public static boolean isSubcriptionCompatibleWithVAPID(WampDict subscriptionInfo)
    {
        return (subscriptionInfo != null && subscriptionInfo.get("keys") != null);
    }
    
    
    public static void notifyWithVAPID(String app, WampDict subscriptionInfo, String rel_link, String msg) throws Exception    
    {
        String endpoint = subscriptionInfo.getText("endpoint");
        
        WampDict keys = (WampDict)subscriptionInfo.get("keys");
        
        String authBase64Url = keys.getText("auth");
        String authBase64 = Base64.convertBase64UrlToBase64(authBase64Url);
        byte[] authBytes = Base64.decodeBase64ToByteArray(authBase64); 
        
        String keyBase64Url = keys.getText("p256dh");
        String keyBase64 = Base64.convertBase64UrlToBase64(keyBase64Url);
        byte[] keyBytes = Base64.decodeBase64ToByteArray(keyBase64);
        
        if(msg != null) {
            byte[] payload = msg.getBytes();
            
            NotificationService ns = Storage.findEntity(NotificationService.class, app);
            PrivateKey vapidPrivateKey = Utils.loadPrivateKey(ns.getPrivateKey());
            PublicKey  vapidPublicKey = Utils.loadPublicKey(ns.getPublicKey());
            KeyPair    vapidKeyPair = new KeyPair(vapidPublicKey, vapidPrivateKey);
        
            // Create a notification with the endpoint, userPublicKey from the subscription and a custom payload
            Notification notification = new Notification(
              endpoint,
              getUserPublicKey(keyBytes),
              authBytes,
              payload
            );


            PushAsyncService pushAsyncService = new PushAsyncService(vapidKeyPair);        
            pushAsyncService.send(notification, Encoding.AES128GCM);
            
            //PushService pushService = new PushService(vapidKeyPair);        
            //HttpResponse response = pushService.send(notification, Encoding.AES128GCM);
            //System.out.println(response);
            
        }
    }
    

    /**
     * Returns the base64 encoded public key as a PublicKey object
     */
    private static PublicKey getUserPublicKey(byte[] keyBytes) throws Exception 
    {
        KeyFactory kf = KeyFactory.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        ECNamedCurveParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec("secp256r1");
        ECPoint point = ecSpec.getCurve().decodePoint(keyBytes);
        ECPublicKeySpec pubSpec = new ECPublicKeySpec(point, ecSpec);

        return kf.generatePublic(pubSpec);
    }
    
}
