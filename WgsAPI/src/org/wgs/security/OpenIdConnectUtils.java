package org.wgs.security;

import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.glassfish.grizzly.http.util.URLDecoder;

import org.wgs.util.Base64;
import org.wgs.util.Social;
import org.wgs.util.Storage;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampException;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.type.WampConnectionState;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


public class OpenIdConnectUtils 
{
    private static final Logger logger = Logger.getLogger(OpenIdConnectUtils.class.toString());
    
    
    public static WampDict getProviders(String redirectUri) 
    {
        WampDict retval = new WampDict();
        WampList providers = new WampList();

        Calendar now = Calendar.getInstance();        
        ArrayList<String> domains = new ArrayList<String>();
        EntityManager manager = null;
        
        try {
            manager = Storage.getEntityManager();
            TypedQuery<OpenIdConnectProvider> queryProviders = manager.createNamedQuery("OpenIdConnectProvider.findAll", OpenIdConnectProvider.class);
            for(OpenIdConnectProvider provider : queryProviders.getResultList()) {
                String domain = provider.getDomain();
                if(!domains.contains(domain) && !"defaultProvider".equals(domain)) {
                    WampDict node = new WampDict();
                    try {
                        //node.put("registrationEndpoint", provider.getRegistrationEndpointUrl());
                        node.put("name", domain);
                        node.put("url", getAuthURL(redirectUri, domain, null));  // Auto registration with OpenID Connect provider
                        providers.add(node);

                    } catch(Exception ex) { }
                    domains.add(domain);
                }
            }

            retval.put("_oauth2_providers", providers);
            
        } catch(Exception ex) {
            
            logger.warning("Auth provider error: " + ex.getMessage());
        
        } finally {
            if(manager != null) {
                try { manager.close(); }
                catch(Exception ex) { }
            }            
        }
        
        return retval;
    }
            
    
    public static String getAuthURL(String redirectUri, String principal, String state) throws Exception
    {
        String retval = null;
        String providerDomain = null;
        
        if(principal == null || principal.length() == 0) {
            providerDomain = "defaultProvider";            
        } else {
            try { 
                String normalizedIdentityURL = principal;
                if(normalizedIdentityURL.indexOf("://") == -1) normalizedIdentityURL = "https://" + principal;
                URL url = new URL(normalizedIdentityURL); 
                providerDomain = url.getHost();
            } finally {
                if(providerDomain == null) {
                    System.err.println("Unsupported OpenID Connect principal format");
                    throw new WampException(null, "wgs.error.oidc_error", null, null);
                }
            }
        }
        
        
        redirectUri = redirectUri + ((redirectUri.indexOf("?") == -1)?"?":"&") + "provider=" + URLEncoder.encode(providerDomain,"utf8");
        
        
        EntityManager manager = null;
        try {
            manager = Storage.getEntityManager();
            OpenIdConnectClientPK oidcId = new OpenIdConnectClientPK(providerDomain, redirectUri);
            OpenIdConnectClient oidcClient = manager.find(OpenIdConnectClient.class, oidcId);
            if(oidcClient == null && !providerDomain.equals("defaultProvider")) {
                
                OpenIdConnectProvider provider = manager.find(OpenIdConnectProvider.class, providerDomain);
                if(provider == null) {
                    ObjectNode oidcConfig = OpenIdConnectProvider.discover(principal);
                    provider = new OpenIdConnectProvider();
                    provider.setDomain(providerDomain);
                    provider.setDynamic(true);
                    provider.setRegistrationEndpointUrl(oidcConfig.get("registration_endpoint").asText());
                    provider.setAuthEndpointUrl(oidcConfig.get("authorization_endpoint").asText());
                    provider.setAccessTokenEndpointUrl(oidcConfig.get("token_endpoint").asText());
                    provider.setUserInfoEndpointUrl(oidcConfig.get("userinfo_endpoint").asText());
                    provider = Storage.saveEntity(provider);
                }

                if(provider.getDynamic() == false) {
                    throw new Exception("OpenID Connect provider '" + providerDomain + "' without client credentials nor dynamic registration support");
                } else {
                    try {
                        // register OIDC client
                        ObjectNode oidcClientRegistrationResponse = provider.registerClient("wgs", redirectUri);
                        oidcClient = new OpenIdConnectClient();
                        oidcClient.setProvider(provider);
                        oidcClient.setRedirectUri(redirectUri);
                        oidcClient.load(oidcClientRegistrationResponse);
                        oidcClient = Storage.saveEntity(oidcClient);
                    } catch(Exception ex) {
                        // OIDC provider without dynamic registration support.
                        provider.setDynamic(false);
                        provider = Storage.saveEntity(provider);
                    }
                }
            }
            
            if(oidcClient != null) {
                Calendar now = Calendar.getInstance();
                if(oidcClient.getClientExpiration() != null && now.after(oidcClient.getClientExpiration())) {
                    try {
                        oidcClient.updateClientCredentials();
                    } catch(Exception ex) {
                        Storage.removeEntity(oidcClient);
                        
                        OpenIdConnectProvider provider = manager.find(OpenIdConnectProvider.class, providerDomain);
                        ObjectNode oidcClientRegistrationResponse = provider.registerClient("wgs", redirectUri);
                        oidcClient.load(oidcClientRegistrationResponse);
                    }
                    oidcClient = Storage.saveEntity(oidcClient);
                }
                
                String oidcAuthEndpointUrl = oidcClient.getProvider().getAuthEndpointUrl();
                String clientId = oidcClient.getClientId();
                retval = oidcAuthEndpointUrl + "?response_type=code&access_type=offline&scope=" + URLEncoder.encode(oidcClient.getProvider().getScopes(),"utf8") + "&client_id=" + URLEncoder.encode(clientId,"utf8") + "&approval_prompt=force&redirect_uri=" + URLEncoder.encode(redirectUri,"utf8");
            }
            
            if(retval == null) {
                throw new Exception("Unknown provider for domain " + providerDomain);
            }
            
        } catch(Exception ex) {
            System.err.println("OpenID Connect provider error: " + ex.getMessage());
            throw new WampException(null, "wgs.error.oidc_error", null, null);
        
        } finally {
            if(manager != null) {
                try { manager.close(); }
                catch(Exception ex) { }
            }            
        }
        
        if(state != null) retval = retval + "&state=" + URLEncoder.encode(state, "utf8");
        return retval;
    }    
    
    
    
    public static WampDict verifyCodeFlow(WampApplication wampApp, WampSocket socket, String code, WampDict data) throws Exception
    {
        User usr = null;
        EntityManager manager = null;
        Calendar now = Calendar.getInstance();

        try {
            String providerDomain = data.getText("authprovider");
            String redirectUri = data.getText("_oauth2_redirect_uri");
            
            manager = Storage.getEntityManager();
            OpenIdConnectClientPK oidcPK = new OpenIdConnectClientPK(providerDomain, redirectUri);
            OpenIdConnectClient oidcClient = manager.find(OpenIdConnectClient.class, oidcPK);
            if(oidcClient == null) {
                System.err.println("Unknown OpenId Connect provider domain");
                throw new WampException(null, "wgs.error.unknown_oidc_provider", null, null);
            }

            String accessTokenResponse = oidcClient.getAccessTokenResponse(code);
            logger.fine("AccessToken endpoint response: " + accessTokenResponse);
            
            if(providerDomain.endsWith("facebook.com")) {
                
                int pos = accessTokenResponse.indexOf("&");
                String accessToken = URLDecoder.decode(accessTokenResponse.substring("access_token=".length(), pos));
                String expires = accessTokenResponse.substring(pos + "&expires=".length());
                Calendar expiration = Calendar.getInstance();
                expiration.add(Calendar.SECOND, Integer.parseInt(expires));
                
                String userInfo = oidcClient.getProvider().getUserInfo(accessToken);
                System.out.println("user info: " + userInfo);

                ObjectMapper mapper = new ObjectMapper();
                ObjectNode   userInfoNode = (ObjectNode)mapper.readTree(userInfo);
                
                String id = userInfoNode.get("id").asText();
                
                usr = UserRepository.findUserByLoginAndDomain(id, providerDomain);
                if(usr == null) {
                    usr = new User();
                    usr.setUid(UUID.randomUUID().toString());
                    usr.setDomain(providerDomain);
                    usr.setLogin(id);
                    usr.setAdministrator(false);
                }
                
                usr.setName(userInfoNode.get("name").asText());
                if(userInfoNode.has("email")) usr.setEmail(userInfoNode.get("email").asText());
                usr.setPicture("https://graph.facebook.com/"+id+"/picture");
                usr.setTokenCaducity(expiration);                
                usr.setAccessToken(accessToken);
                
            } else {
                // OpenID Connect

                ObjectMapper mapper = new ObjectMapper();
                ObjectNode   response = (ObjectNode)mapper.readTree(accessTokenResponse);

                if(response != null && response.has("id_token") && !response.get("id_token").isNull()) {
                    String idTokenJWT = response.get("id_token").asText();
                    //System.out.println("Encoded id_token: " + idToken);

                    int pos1 = idTokenJWT.indexOf(".")+1;
                    int pos2 = idTokenJWT.indexOf(".", pos1);
                    String idTokenData = idTokenJWT.substring(pos1, pos2);
                    while((idTokenData.length() % 4) != 0) idTokenData = idTokenData + "=";
                    idTokenData = Base64.decodeBase64ToString(idTokenData);
                    logger.fine("Decoded id_token: " + idTokenData);

                    ObjectNode idTokenNode = (ObjectNode)mapper.readTree(idTokenData);          
                    String issuer = idTokenNode.get("iss").asText();
                    String login = idTokenNode.get("sub").asText();
                    usr = UserRepository.findUserByLoginAndDomain(login, providerDomain);
                    if(usr == null) {
                        usr = new User();
                        usr.setUid(UUID.randomUUID().toString());
                        usr.setDomain(providerDomain);
                        usr.setLogin(login);
                        usr.setAdministrator(false);
                    }
                    
                    if( (usr != null) && (usr.getProfileCaducity() != null) && (usr.getProfileCaducity().after(now)) )  {
                        // Use cached UserInfo from local database
                        logger.fine("Cached OIDC User: " + usr);
                        wampApp.onUserLogon(socket, usr, WampConnectionState.AUTHENTICATED);

                    } else if(response.has("access_token")) {
                        // Get UserInfo from OpenId Connect Provider
                        String accessToken = response.get("access_token").asText();
                        System.out.println("Access token: " + accessToken);

                        String userInfo = oidcClient.getProvider().getUserInfo(accessToken);
                        logger.fine("OIDC UserInfo: " + userInfo);

                        ObjectNode userInfoNode = (ObjectNode)mapper.readTree(userInfo);

                        usr.setName(userInfoNode.get("name").asText());
                        if(userInfoNode.has("email")) usr.setEmail(userInfoNode.get("email").asText());
                        if(userInfoNode.has("picture")) usr.setPicture(userInfoNode.get("picture").asText());
                        if(idTokenNode.has("exp")) {
                            Calendar caducity = Calendar.getInstance();
                            caducity.setTimeInMillis(idTokenNode.get("exp").asLong()*1000l);
                            usr.setProfileCaducity(caducity);
                        }

                    } 


                    if(response.has("refresh_token")) {
                        usr.setRefreshToken(response.get("refresh_token").asText());
                    }

                    if(response.has("access_token")) {
                        usr.setAccessToken(response.get("access_token").asText());
                        if(response.has("expires_in")) {
                            int expires_in = response.get("expires_in").asInt();
                            Calendar expiration = Calendar.getInstance();
                            expiration.add(Calendar.SECOND, expires_in);
                            usr.setTokenCaducity(expiration);
                        }
                    }

                    if(usr!= null && data.has("notification_channel")) {
                        String notificationChannel = data.getText("notification_channel");
                        if(!notificationChannel.equals(usr.getNotificationChannel())) {
                            usr.setNotificationChannel(notificationChannel);
                        }
                    }

                }
            }
            
            
            if(usr != null) {
                Social.getFriends(usr);
                usr.setLastLoginTime(now);
                usr = Storage.saveEntity(usr);
                wampApp.onUserLogon(socket, usr, WampConnectionState.AUTHENTICATED);                           
            }
            
            
        } catch(Exception ex) {
            usr = null;
            logger.log(Level.SEVERE, "OpenID Connect error: " + ex.getClass().getName() + ":" + ex.getMessage(), ex);
            
        } finally {
            if(manager != null) {
                try { manager.close(); }
                catch(Exception ex) { }
            }
        }

        if(usr == null) {
            System.err.println("OpenID Connect protocol error");
            throw new WampException(null, "wgs.error.oidc_error", null, null);
        }
        
        return usr.toWampObject(true);
    }        
}
