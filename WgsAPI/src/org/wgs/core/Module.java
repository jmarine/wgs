/**
 * WebSocket Game services implementation
 *
 * @author Jordi Marine Fort 
 */

package org.wgs.core;


import java.math.BigDecimal;
import java.net.URL;
import java.net.URLEncoder;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.wgs.util.Storage;
import org.wgs.entity.User;
import org.wgs.entity.UserId;
import org.wgs.entity.OpenIdConnectClient;
import org.wgs.entity.OpenIdConnectClientPK;
import org.wgs.entity.OpenIdConnectProvider;
import org.wgs.util.Base64;
import org.wgs.wamp.MatchEnum;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampCallController;
import org.wgs.wamp.WampCallOptions;
import org.wgs.wamp.WampConnectionState;
import org.wgs.wamp.WampDict;
import org.wgs.wamp.WampException;
import org.wgs.wamp.WampList;
import org.wgs.wamp.WampMetaTopic;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampModuleName;
import org.wgs.wamp.WampObject;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampPublishOptions;
import org.wgs.wamp.WampRPC;
import org.wgs.wamp.WampServices;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.WampSubscription;
import org.wgs.wamp.WampSubscriptionOptions;
import org.wgs.wamp.WampTopic;
import org.wgs.wamp.WampTopicOptions;


@WampModuleName(Module.WGS_MODULE_NAME)
public class Module extends WampModule 
{
    private static final Logger logger = Logger.getLogger(Module.class.toString());
    public  static final String WGS_MODULE_NAME = "wgs";
    
    private WampApplication wampApp = null;
    private Map<Long,Client> clients = new ConcurrentHashMap<Long,Client>();
    private Map<String, Application> applications = new ConcurrentHashMap<String,Application>();
    private Map<String, Group> groups = new ConcurrentHashMap<String,Group>();
    
    public Module(WampApplication app)
    {
        super(app);
        this.wampApp = app;
        
        WampServices.createTopic(app, getFQtopicURI("apps_event"), null);

        try {
            List<Application> apps = Storage.findEntities(Application.class, "wgs.findAllApps");
            for(Application a : apps) {
                System.out.println("Application found in DB: " + a.getName());
                registerApplication(a);
            }
        } catch(Exception ex) {
            System.out.println("Error loading WGS applications: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    
    public Client getClient(Long sessionId)
    {
        return clients.get(sessionId);
    }
    
    public Group getGroup(String gid)
    {
        return groups.get(gid);
    }
    
    private String getFQtopicURI(String topicName)
    {
        return WGS_MODULE_NAME + "." + topicName;
    }

    @Override
    public Object onCall(WampCallController task, WampSocket socket, String method, WampList args, WampDict argsKw, WampCallOptions callOptions) throws Exception 
    {
        Object retval = null;
        if(method.equals("wgs.list_groups")) {
            String appId = args.getText(0);

            WampList metaTopics = new WampList();
            metaTopics.add(WampMetaTopic.SUBSCRIBER_ADDED);
            metaTopics.add(WampMetaTopic.SUBSCRIBER_REMOVED);

            WampDict filterOptions = (WampDict)args.get(1);
            GroupFilter options = new GroupFilter(this, filterOptions);
            options.setMetaTopics(metaTopics);
            
            if(appId.indexOf("*") != -1) options.setMatchType(MatchEnum.wildcard);
             
            //WampServices.subscribeClientWithTopic(wampApp, socket, null, getFQtopicURI("app_event:"+appId), options);
            retval = listGroups(socket, appId, options);
        } else {
            retval = super.onCall(task, socket, method, args, argsKw, callOptions);
        }
        return retval;
    }


    @Override
    public void onConnect(WampSocket socket) throws Exception {
        super.onConnect(socket);
        Client client = new Client();
        client.setSocket(socket);
        socket.setState(WampConnectionState.ANONYMOUS);
        if(socket.getUserPrincipal() != null) {
            EntityManager manager = Storage.getEntityManager();
            UserId userId = new UserId(User.LOCAL_USER_DOMAIN, socket.getUserPrincipal().getName());
            User usr = manager.find(User.class, userId);
            manager.close();
            socket.setUserPrincipal(usr);
            socket.setState(WampConnectionState.AUTHENTICATED);
        }
        clients.put(socket.getSessionId(), client);
    }
    
    @Override
    public void onDisconnect(WampSocket socket) throws Exception {
        Client client = clients.get(socket.getSessionId());
        socket.setState(WampConnectionState.OFFLINE);
        for(String gid : client.getGroups().keySet()) {
            exitGroup(socket, gid);
        }
        clients.remove(socket.getSessionId());
        super.onDisconnect(socket);
    }
    
    
    @WampRPC(name="register")
    public WampDict registerUser(WampSocket socket, WampDict data) throws Exception
    {
        boolean user_valid = false;
        User usr = null;
        
        Client client = clients.get(socket.getSessionId());

        String user = data.getText("user");
        UserId userId = new UserId(User.LOCAL_USER_DOMAIN, user);
        
        EntityManager manager = Storage.getEntityManager();
        usr = manager.find(User.class, userId);
        manager.close();
        
        if(usr != null) throw new WampException(WGS_MODULE_NAME + ".user_already_exists", "The user is reserved by another user");
        
        usr = new User();
        usr.setProfileCaducity(null);
        usr.setId(userId);
        if(user.length() == 0) usr.setName("");
        else usr.setName(Character.toUpperCase(user.charAt(0)) + user.substring(1));
        usr.setPassword(data.getText("password"));
        usr.setEmail(data.getText("email"));
        usr.setAdministrator(false);
        usr.setLastLoginTime(Calendar.getInstance());
        usr = Storage.saveEntity(usr);

        socket.setUserPrincipal(usr);
        socket.setState(WampConnectionState.AUTHENTICATED);
        
        return usr.toWampObject();
    }
    
    
    @WampRPC(name="get_user_info")
    public WampDict getUserInfo(WampSocket socket, WampDict data) throws Exception
    {
        boolean user_valid = false;
        Client client = clients.get(socket.getSessionId());

        if(client == null || client.getSocket().getState() != WampConnectionState.AUTHENTICATED) {
            throw new WampException(WGS_MODULE_NAME + ".anonymous_user", "User not authenticated");
        }
        
        User usr = client.getUser();
        if(usr == null) {
            throw new WampException(WGS_MODULE_NAME + ".unknown_userinfo", "User info not registered");
        }
        
        return usr.toWampObject();
    }

    
    @WampRPC(name="openid_connect_providers")
    public WampDict openIdConnectProviders(WampSocket socket, WampDict data) throws Exception
    {
        WampDict retval = new WampDict();
        WampList providers = new WampList();
        String redirectUri = data.getText("redirect_uri");
        redirectUri = redirectUri + ((redirectUri.indexOf("?") == -1)?"?":"&") + "provider=%";
        
        Calendar now = Calendar.getInstance();        
        EntityManager manager = null;
        ArrayList<String> domains = new ArrayList<String>();
        
        try {
            manager = Storage.getEntityManager();

            TypedQuery<OpenIdConnectClient> queryClients = manager.createNamedQuery("OpenIdConnectClient.findByRedirectUri", OpenIdConnectClient.class);
            queryClients.setParameter("uri", redirectUri);
            for(OpenIdConnectClient oic : queryClients.getResultList()) {
                String providerDomain = oic.getProvider().getDomain();
                if(!domains.contains(providerDomain) && !"defaultProvider".equals(providerDomain)) {
                    
                    if(oic.getClientExpiration() != null && now.after(oic.getClientExpiration())) {
                        try {
                            oic.updateClientCredentials();
                        } catch(Exception ex) {
                            System.out.println("Error updating client credentials: " + ex.getMessage());
                            
                            Storage.removeEntity(oic);

                            OpenIdConnectProvider provider = manager.find(OpenIdConnectProvider.class, providerDomain);
                            ObjectNode oicClientRegistrationResponse = provider.registerClient("wgs", oic.getRedirectUri());
                            oic.load(oicClientRegistrationResponse);
                        }
                        oic = Storage.saveEntity(oic);
                    }                    
                    
                    String clientId = oic.getClientId();
                    String oicAuthEndpointUrl = oic.getProvider().getAuthEndpointUrl();
                    String uri = oicAuthEndpointUrl + "?response_type=code&access_type=offline&scope=" + URLEncoder.encode(oic.getProvider().getScopes(),"utf8") + "&client_id=" + URLEncoder.encode(clientId,"utf8") + "&approval_prompt=force&redirect_uri=" + URLEncoder.encode(oic.getRedirectUri(),"utf8");

                    WampDict node = new WampDict();
                    node.put("name", providerDomain);
                    node.put("authEndpoint", uri);
                    providers.add(node);
                    domains.add(providerDomain);
                }
            }
            
            TypedQuery<OpenIdConnectProvider> queryProviders = manager.createNamedQuery("OpenIdConnectProvider.findDynamic", OpenIdConnectProvider.class);
            for(OpenIdConnectProvider provider : queryProviders.getResultList()) {
                String domain = provider.getDomain();
                if(!domains.contains(domain) && !"defaultProvider".equals(domain)) {
                    WampDict node = new WampDict();
                    node.put("name", domain);
                    node.put("registrationEndpoint", provider.getRegistrationEndpointUrl());
                    providers.add(node);
                    domains.add(domain);
                }
            }

            retval.put("providers", providers);
            
        } catch(Exception ex) {
            
            throw new WampException(WGS_MODULE_NAME + ".oic_error", "OpenID Connect provider error: " + ex.getMessage());
        
        } finally {
            if(manager != null) {
                try { manager.close(); }
                catch(Exception ex) { }
            }            
        }
        
        return retval;
    }
            
    
    @WampRPC(name="openid_connect_login_url")
    public String openIdConnectLoginUrl(WampSocket socket, WampDict data) throws Exception
    {
        String retval = null;
        String providerDomain = null;
        String principal = data.getText("principal");
        String state = data.has("state")? data.getText("state") : null;
        
        if(principal == null || principal.length() == 0) {
            providerDomain = "defaultProvider";            
        } else {
            try { 
                String normalizedIdentityURL = principal;
                if(normalizedIdentityURL.indexOf("://") == -1) normalizedIdentityURL = "https://" + principal;
                URL url = new URL(normalizedIdentityURL); 
                providerDomain = url.getHost();
            } finally {
                if(providerDomain == null) throw new WampException(WGS_MODULE_NAME + ".oic_error", "Unsupported OpenID Connect principal format");
            }
        }
        
        
        String redirectUri = data.getText("redirect_uri");
        redirectUri = redirectUri + ((redirectUri.indexOf("?") == -1)?"?":"&") + "provider=" + URLEncoder.encode(providerDomain,"utf8");
        
        
        EntityManager manager = null;
        try {
            manager = Storage.getEntityManager();
            OpenIdConnectClientPK oicId = new OpenIdConnectClientPK(providerDomain, redirectUri);
            OpenIdConnectClient oic = manager.find(OpenIdConnectClient.class, oicId);
            if(oic == null && !providerDomain.equals("defaultProvider")) {
                
                OpenIdConnectProvider provider = manager.find(OpenIdConnectProvider.class, providerDomain);
                if(provider == null) {
                    ObjectNode oicConfig = OpenIdConnectProvider.discover(principal);
                    provider = new OpenIdConnectProvider();
                    provider.setDomain(providerDomain);
                    provider.setRegistrationEndpointUrl(oicConfig.get("registration_endpoint").asText());
                    provider.setAuthEndpointUrl(oicConfig.get("authorization_endpoint").asText());
                    provider.setAccessTokenEndpointUrl(oicConfig.get("token_endpoint").asText());
                    provider.setUserInfoEndpointUrl(oicConfig.get("userinfo_endpoint").asText());
                    provider = Storage.saveEntity(provider);
                }

                ObjectNode oicClientRegistrationResponse = provider.registerClient("wgs", redirectUri);
                if(!provider.getDynamic()) {
                    provider.setDynamic(true);
                    provider = Storage.saveEntity(provider);
                }
                
                oic = new OpenIdConnectClient();
                oic.setProvider(provider);
                oic.setRedirectUri(redirectUri);
                oic.load(oicClientRegistrationResponse);
                oic = Storage.saveEntity(oic);
            }
            
            if(oic != null) {
                Calendar now = Calendar.getInstance();
                if(oic.getClientExpiration() != null && now.after(oic.getClientExpiration())) {
                    try {
                        oic.updateClientCredentials();
                    } catch(Exception ex) {
                        Storage.removeEntity(oic);
                        
                        OpenIdConnectProvider provider = manager.find(OpenIdConnectProvider.class, providerDomain);
                        ObjectNode oicClientRegistrationResponse = provider.registerClient("wgs", redirectUri);
                        oic.load(oicClientRegistrationResponse);
                    }
                    oic = Storage.saveEntity(oic);
                }
                
                String oicAuthEndpointUrl = oic.getProvider().getAuthEndpointUrl();
                String clientId = oic.getClientId();
                retval = oicAuthEndpointUrl + "?response_type=code&access_type=offline&scope=" + URLEncoder.encode(oic.getProvider().getScopes(),"utf8") + "&client_id=" + URLEncoder.encode(clientId,"utf8") + "&approval_prompt=force&redirect_uri=" + URLEncoder.encode(redirectUri,"utf8");
            }
            
            if(retval == null) {
                throw new Exception("Unknown provider for domain " + providerDomain);
            }
            
        } catch(Exception ex) {
            
            throw new WampException(WGS_MODULE_NAME + ".oic_error", "OpenID Connect provider error: " + ex.getMessage());
        
        } finally {
            if(manager != null) {
                try { manager.close(); }
                catch(Exception ex) { }
            }            
        }
        
        if(state != null) retval = retval + "&state=" + URLEncoder.encode(state, "utf8");
        return retval;
    }
    
    @WampRPC(name="openid_connect_auth")
    public WampDict openIdConnectAuth(WampSocket socket, WampDict data) throws Exception
    {
        User usr = null;
        EntityManager manager = null;
        Client client = clients.get(socket.getSessionId());

        try {
            String code = data.getText("code");
            String providerDomain = data.getText("provider");
            String redirectUri = data.getText("redirect_uri");
            
            manager = Storage.getEntityManager();
            OpenIdConnectClientPK oicId = new OpenIdConnectClientPK(providerDomain, redirectUri);
            OpenIdConnectClient oic = manager.find(OpenIdConnectClient.class, oicId);
            if(oic == null) throw new WampException(WGS_MODULE_NAME + ".unknown_oidc_provider", "Unknown OpenId Connect provider domain");

            String accessTokenResponse = oic.getAccessTokenResponse(code);
            logger.fine("AccessToken endpoint response: " + accessTokenResponse);

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

                ObjectNode   idTokenNode = (ObjectNode)mapper.readTree(idTokenData);          


                String issuer = idTokenNode.get("iss").asText();
                UserId userId = new UserId(providerDomain, idTokenNode.get("sub").asText());
                usr = manager.find(User.class, userId);

                Calendar now = Calendar.getInstance();
                if( (usr != null) && (usr.getProfileCaducity() != null) && (usr.getProfileCaducity().after(now)) )  {
                    // Use cached UserInfo from local database
                    logger.fine("Cached OIC User: " + usr);
                    socket.setUserPrincipal(usr);
                    socket.setState(WampConnectionState.AUTHENTICATED);
                    
                } else if(response.has("access_token")) {
                    // Get UserInfo from OpenId Connect Provider
                    String accessToken = response.get("access_token").asText();
                    System.out.println("Access token: " + accessToken);

                    String userInfo = oic.getProvider().getUserInfo(accessToken);
                    logger.fine("OIC UserInfo: " + userInfo);

                    ObjectNode userInfoNode = (ObjectNode)mapper.readTree(userInfo);

                    usr = new User();
                    usr.setId(userId);
                    usr.setName(userInfoNode.get("name").asText());
                    usr.setAdministrator(false);

                    if(userInfoNode.has("email")) usr.setEmail(userInfoNode.get("email").asText());
                    if(userInfoNode.has("picture")) usr.setPicture(userInfoNode.get("picture").asText());
                    if(idTokenNode.has("exp")) {
                        Calendar caducity = Calendar.getInstance();
                        caducity.setTimeInMillis(idTokenNode.get("exp").asLong()*1000l);
                        usr.setProfileCaducity(caducity);
                    }

                    usr = Storage.saveEntity(usr);

                    socket.setUserPrincipal(usr);
                    socket.setState(WampConnectionState.AUTHENTICATED);
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
                
                usr.setLastLoginTime(now);
                usr = Storage.saveEntity(usr);
                
                oic.getFriends(usr);
                
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

        if(usr == null) throw new WampException(WGS_MODULE_NAME + ".oic_error", "OpenID Connect protocol error");
        return usr.toWampObject();
    }    
    
    
    
    @WampRPC(name="list_apps")
    public WampDict listApps() throws Exception
    {
        // TODO: Filter by domain
        WampDict retval = new WampDict();
        WampList appArray = new WampList();
        for(Application app : applications.values()) {
            appArray.add(app.toWampObject());
        }
        retval.put("apps", appArray);

        return retval;
    }
    
    
    private void registerApplication(Application app) {
        WampServices.createTopic(wampApp, getFQtopicURI("app_event:"+app.getAppId()), null);
        applications.put(app.getAppId(), app);
    }
    
    private void unregisterApplication(Application app) {
        WampServices.removeTopic(wampApp, getFQtopicURI("app_event:"+app.getAppId()));
        applications.remove(app.getAppId());
    }
    

    @WampRPC(name="new_app")
    public WampDict newApp(WampSocket socket, WampDict data) throws Exception
    {
        // TODO: check it doesn't exists

        boolean valid = false;
        Client client = clients.get(socket.getSessionId());
        if(socket.getState() != WampConnectionState.AUTHENTICATED) throw new WampException(WGS_MODULE_NAME + ".unknown_user", "The user hasn't logged in");
        
        // TODO: check user is administrator
        //if(!client.getUser().isAdministrator()) throw new WampException(MODULE_URL + "adminrequired", "The user is not and administrator");
        
        Application app = new Application();
        app.setAppId(UUID.randomUUID().toString());
        app.setAdminUser(client.getUser());
        app.setName(data.getText("name"));
        app.setDomain(data.getText("domain"));
        app.setVersion(data.getLong("version").intValue());
        app.setMaxScores(data.getLong("max_scores").intValue());
        app.setDescendingScoreOrder(data.getBoolean("desc_score_order"));
        app.setMaxMembers(data.getLong("max").intValue());
        app.setMinMembers(data.getLong("min").intValue());
        app.setDeltaMembers(data.getLong("delta").intValue());
        app.setAlliancesAllowed(data.getBoolean("alliances"));
        app.setDynamicGroup(data.getBoolean("dynamic"));
        app.setObservableGroup(data.getBoolean("observable"));
        app.setAIavailable(data.getBoolean("ai_available"));

        WampList roles = (WampList)data.get("roles");
        for(int i = 0; i < roles.size(); i++) {
            String roleName = roles.getText(i);
            int roleNameLen = roleName.length();

            boolean optional = (roleNameLen > 0) && (roleName.charAt(roleNameLen-1) == '*' || roleName.charAt(roleNameLen-1) == '?');
            boolean multiple = (roleNameLen > 0) && (roleName.charAt(roleNameLen-1) == '*' || roleName.charAt(roleNameLen-1) == '+');
            if(multiple || optional) {
                roleName = roleName.substring(0, roleNameLen-1);
                System.out.println("Role: " + roleName);
            }

            Role role = new Role();
            role.setApplication(app);
            role.setName(roleName);
            role.setRequired(!optional);
            role.setMultiple(multiple);

            app.addRole(role);
        }

        app = Storage.saveEntity(app);
        registerApplication(app);
        valid = true;

        WampDict event = broadcastAppInfo(socket, app, "app_created", true);
        return event;
    }
        
    
    @WampRPC(name="delete_app")
    public WampDict deleteApp(WampSocket socket, WampDict param) throws Exception
    {
        // TODO: check user is administrator of app
        // TODO: delete groups
        
        WampDict event = null;
        String appId = param.getText("app");

        Application app = applications.get(appId);
        if(app != null) {
            EntityManager manager = Storage.getEntityManager();
            EntityTransaction tx = manager.getTransaction();
            tx.begin();
            
            Query query1 = manager.createQuery("DELETE FROM GroupAction a WHERE a.applicationGroup.application = :app");
            query1.setParameter("app", app);
            int rows1 = query1.executeUpdate();
            
            Query query2 = manager.createQuery("DELETE FROM GroupMember m WHERE m.applicationGroup.application = :app");
            query2.setParameter("app", app);
            int rows2 = query2.executeUpdate();
            
            Query query3 = manager.createQuery("DELETE FROM AppGroup g WHERE g.application = :app");
            query3.setParameter("app", app);
            int rows3 = query3.executeUpdate();
            
            Query query4 = manager.createQuery("DELETE FROM LeaderBoard lb WHERE lb.application = :app");
            query4.setParameter("app", app);
            int rows4 = query4.executeUpdate();
            
            tx.commit();
            manager.close();
            
            Storage.removeEntity(app);
            unregisterApplication(app);
            event = broadcastAppInfo(socket, app, "app_deleted", true);
            return event;
        } else {
            throw new WampException(WGS_MODULE_NAME + ".appid_not_found", "AppId " + appId + " doesn't exist");
        }
    }
    
    
    private WampDict broadcastAppInfo(WampSocket socket, Application app, String cmd, boolean excludeMe) throws Exception
    {
        WampDict event = app.toWampObject();
        event.put("cmd", cmd);
        socket.publishEvent(WampServices.getTopic(getFQtopicURI("apps_event")), null, event, excludeMe, false);
        return event;
    }

    
    @WampRPC(name="open_group")
    public synchronized WampDict openGroup(WampSocket socket, String appId, String gid, WampDict options) throws Exception
    {
        Group   g = null;
        boolean valid   = false;
        boolean created = false;
        boolean joined  = false;
        boolean autoMatchMode = false;
        boolean spectator = false;
        if( (options != null) && (options.has("spectator")) ) {
            spectator = options.has("spectator")? options.getBoolean("spectator") : false;
        }

        
        EntityManager manager = Storage.getEntityManager();
        manager.getTransaction().begin();
        
        Client client = clients.get(socket.getSessionId());
        
        if(gid != null) {
            if(gid.equals("automatch")) {
                Application app = applications.get(appId);
                if(app != null) {
                    autoMatchMode = true;
                    
                    String jpaQuery = "SELECT DISTINCT OBJECT(g) FROM AppGroup g WHERE g.state = org.wgs.core.GroupState.OPEN AND g.autoMatchEnabled = TRUE AND g.autoMatchCompleted = FALSE AND g.application = :application";
                    // TODO: automatch criteria (role, ELO range, game variant, time criteria,...)                    
                    TypedQuery<Group> groupQuery = manager.createQuery(jpaQuery, Group.class);
                    groupQuery.setParameter("application", app);
                    List<Group> groupList = groupQuery.getResultList();
                    for(Group tmp : groupList) {
                        manager.lock(tmp, LockModeType.PESSIMISTIC_WRITE);
                        valid = (tmp != null) && (tmp.isAutoMatchEnabled() && !tmp.isAutoMatchCompleted() && tmp.getState()==GroupState.OPEN);
                        if(valid) {
                            g = groups.get(tmp.getGid());
                            if(g != null) g.setVersion(tmp.getVersion());
                            else g = tmp;
                            break;
                        } else {
                            manager.lock(tmp, LockModeType.NONE);  // FIXME: MySQL holds lock
                        }
                    } 
                    
                }                
                logger.log(Level.INFO, "open_group: search group for automatch");
            } else {
                g = groups.get(gid);
                if(g != null) {
                    logger.log(Level.INFO, "open_group: group found: " + gid);
                    valid = true;
                } 
            }
        } 
        
        if(g != null) {
            String pwd = g.getPassword();
            if( (pwd != null) && (pwd.length()>0) ) {
                String pwd2 = (options!=null && options.has("password"))? options.getText("password") : "";
                if(!pwd.equals(pwd2)) throw new WampException(WGS_MODULE_NAME + ".incorrectpassword", "Incorrect password");
            }
            
        } else if(!spectator) {  
            // create group
            try {
                logger.log(Level.FINE, "open_group: creating new group");
                Application app = applications.get(appId);
                g = new Group();
                g.setGid(UUID.randomUUID().toString());
                g.setApplication(app);
                g.setState(GroupState.OPEN);
                g.setObservableGroup(app.isObservableGroup());
                g.setDynamicGroup(app.isDynamicGroup());
                g.setAlliancesAllowed(app.isAlliancesAllowed());
                g.setMaxMembers(app.getMaxMembers());
                g.setMinMembers(app.getMinMembers());
                g.setDeltaMembers(app.getDeltaMembers());
                g.setAdminUserId( (client.getUser() != null) ? client.getUser().getId() : new UserId("#anonymous-" + client.getSessionId()) );
                g.setAutoMatchEnabled(autoMatchMode);
                g.setAutoMatchCompleted(false);
                if(options != null) {
                    if(options.has("automatch")) {
                        autoMatchMode = options.getBoolean("automatch");
                        g.setAutoMatchEnabled(autoMatchMode);
                    } 
                    if(options.has("hidden")) {
                        g.setHidden(options.has("hidden")? options.getBoolean("hidden") : false);
                    }
                    if(options.has("observable")) {
                        g.setObservableGroup(options.has("observable")? options.getBoolean("observable") : g.getApplication().isObservableGroup());
                    }                    
                    if(!autoMatchMode && options.has("password")) {
                        String password = options.getText("password");
                        g.setPassword( (password!=null && password.length()>0)? password : null);
                    }
                    if(options.has("description")) {
                        g.setDescription(options.getText("description"));
                    }
                }
                
                app.addGroup(g);
                groups.put(g.getGid(), g);

                //updateAppInfo(socket, app, "app_updated", false);

                valid = true;
                created = true;

            } catch(Exception err) {
                // valid = false;
            }

        }

        // generate response:
        WampDict response = (g!=null)? g.toWampObject() : new WampDict();
        response.put("cmd", "user_joined");

        if(valid) {
            Application app = g.getApplication();
            ArrayList<String> requiredRoles = new ArrayList<String>();
            for(Role r : app.getRoles()) {
                if(r.isRequired()) requiredRoles.add(r.getName());
            }
            
            response.put("created", created);
            response.put("app", app.toWampObject());

            String topicName = getFQtopicURI("group_event:" + g.getGid());
            WampTopic topic = WampServices.getTopic(topicName);
            if(topic == null) {
                WampTopicOptions topicOptions = new WampTopicOptions();
                topic = WampServices.createTopic(wampApp, topicName, topicOptions);
            }
            
            //WampSubscriptionOptions subscriptionOptions = new WampSubscriptionOptions(null);
            //WampServices.subscribeClientWithTopic(wampApp, client.getSocket(), null, topicName, subscriptionOptions);
            
            client.addGroup(g);
            WampList conArray = new WampList();
            for(WampSubscription subscription : topic.getSubscriptions()) {
                for(Long sid : subscription.getSessionIds()) {
                    Client c = clients.get(sid);
                    User u = ((c!=null)? c.getUser() : null);
                    String user = ((u == null) ? "" : u.getFQid());
                    String name = ((u == null) ? "" : u.getName());
                    String picture = ((u == null) ? null : u.getPicture());

                    WampDict con = new WampDict();
                    con.put("user", user);
                    con.put("name", name);
                    con.put("picture", picture);
                    con.put("sid", sid);
                    conArray.add(con);
                }
            }
            response.put("connections", conArray);            

            boolean reserved = false;
            int reservedSlot = 0;
            int num_slots = g.getNumSlots();
            if(!spectator) {
                int  avail_slots = 0;
                User currentUser = client.getUser();
                for(int index = 0;
                        (index < Math.max(num_slots, g.getMinMembers()));
                        index++) {

                    Member member = null;
                    member = g.getMember(index);
                    boolean connected = (member != null) && (member.getClient() != null);
                    String user = ((member == null || member.getUser() == null) ? "" : member.getUser().getFQid() );
                    if(!connected && currentUser!=null && user.equals(currentUser.getFQid())) {
                        reserved = true;
                        reservedSlot = index;
                        break;
                    } else if(!connected) {
                        avail_slots++;
                    }
                }

                if(!reserved && avail_slots == 1 && g.getState()==GroupState.OPEN) {
                    g.setAutoMatchCompleted(true);
                }

                if(!reserved && avail_slots == 0) {
                    int step = g.getDeltaMembers();
                    if(step < 1) step = 1;
                    num_slots = Math.min(num_slots+step, g.getMaxMembers());
                }
            }

            int requiredSlot = (options != null && options.has("slot"))? options.getLong("slot").intValue() : -1;
            for(int index = (requiredSlot >= 0)? requiredSlot : 0;
                    ((index < Math.max(num_slots, g.getMinMembers())) || (requiredRoles.size() > 0))
                    && (requiredSlot < 0 || index==requiredSlot);
                    index++) {

                Member member = g.getMember(index);
                if(member == null) {
                    member = new Member();
                    member.setApplicationGroup(g);
                    member.setSlot(index);
                    member.setTeam(1+index);
                    member.setUserType("user");
                    g.setMember(index, member);
                }
                    
                Role role = member.getRole();
                if(role != null) {
                    requiredRoles.remove(role.toString());
                } else if(requiredRoles.size() > 0) {
                    String roleName = requiredRoles.remove(0);
                    member.setRole(g.getApplication().getRoleByName(roleName));
                }

                boolean connected = (member.getClient() != null);
                if(!spectator && !connected && !joined && (!reserved || index == reservedSlot)) {
                    member.setClient(client);
                    member.setState(MemberState.JOINED);
                    member.setUser(client.getUser());
                    if(options != null && options.has("role")) {
                        Role oldRole = member.getRole();
                        String roleName = options.getText("role");
                        role = g.getApplication().getRoleByName(roleName);
                        if(role != null && (oldRole == null || !roleName.equals(oldRole.getName())) ) {
                            requiredRoles.remove(roleName);
                            if(oldRole != null && oldRole.isRequired()) requiredRoles.add(oldRole.getName());
                            member.setRole(role);
                        }
                    }                    

                    joined = true;
                    connected = true;

                    WampDict event = member.toWampObject();
                    event.put("cmd", "user_joined");
                    //event.put("sid", client.getSessionId());
                    //event.put("user", member.getUser().getFQid());
                    //event.put("name", member.getUser().getName());
                    //event.put("picture", member.getUser().getPicture());
                    event.put("gid", g.getGid());
                    event.put("valid", true);

                    socket.publishEvent(WampServices.getTopic(getFQtopicURI("group_event:"+g.getGid())), null, event, true, false);  // exclude Me
                }

            }

            response.put("members", getMembers(g.getGid(), 0));
            
            broadcastAppEventInfo(socket, g, created? "group_created" : "group_updated", false);
            
            g.setVersion(Storage.saveEntity(g).getVersion());

        }

        
        if(valid && !created && !joined) {
            User u = client.getUser();
            Long sid = client.getSessionId();
            String user = ( (u == null) ? "" : u.getFQid() );

            WampDict event = new WampDict();
            event.put("cmd", "user_joined");
            event.put("gid", g.getGid());
            event.put("user", user);
            event.put("name", ((u == null)? "" : u.getName()) );
            event.put("picture", ((u == null)? null : u.getPicture()) );
            event.put("sid", sid);
            event.put("type", "user");
            event.put("valid", valid);
                    
            socket.publishEvent(WampServices.getTopic(getFQtopicURI("group_event:"+g.getGid())), null, event, true, false);  // exclude Me
        }
        
        
        manager.getTransaction().commit();
        manager.close();
        
        return response;
    }
    

    @WampRPC(name="update_group")
    public WampDict updateGroup(WampSocket socket, WampDict node) throws Exception
    {
        // TODO: change group properties (state, observable, etc)
        boolean valid = false;
        boolean broadcastAppInfo = false;
        boolean broadcastGroupInfo = false;
        String appId = node.getText("app");
        String gid = node.getText("gid");

        WampDict response = new WampDict();
        response.put("cmd", "group_updated");
        response.put("sid", socket.getSessionId());
        
        
        Group g = groups.get(gid);
        if(g != null) {
            logger.log(Level.FINE, "open_group: group found: " + gid);
            
            if(node.has("automatch")) {
                boolean autoMatchMode = node.getBoolean("automatch");
                g.setAutoMatchEnabled(autoMatchMode);
                broadcastGroupInfo = true;
            } 

            if(node.has("dynamic")) {
                boolean dynamic = node.getBoolean("dynamic");
                g.setDynamicGroup(dynamic);
                broadcastGroupInfo = true;
            }
            
            if(node.has("alliances")) {
                boolean alliances = node.getBoolean("alliances");
                g.setAlliancesAllowed(alliances);
                broadcastGroupInfo = true;
            }            

            if(node.has("hidden")) {
                boolean hidden = node.getBoolean("hidden");
                g.setHidden(hidden);
                broadcastAppInfo = true;
                broadcastGroupInfo = true;
            }            
            
            if(node.has("observable")) {
                boolean observable = node.getBoolean("observable");
                g.setObservableGroup(observable);
                broadcastAppInfo = true;
                broadcastGroupInfo = true;
            }                                 
            
            if(node.has("data")) {
                String data = node.getText("data");
                g.setData(data);
                broadcastGroupInfo = true;
            }
            
            if(node.has("state")) {
                String state = node.getText("state");
                g.setState(GroupState.valueOf(state));
                broadcastAppInfo = true;
                broadcastGroupInfo = true;                
            }

            
            response.putAll(g.toWampObject());
            if(node.has("state")) {            
                if(g.getState() == GroupState.STARTED) {
                    for(int slot = 0; slot < g.getNumSlots(); slot++) {
                        Member member = g.getMember(slot);
                        if(member != null && member.getClient() != null && socket.getSessionId().equals(member.getClient().getSessionId())) {
                            member.setState(MemberState.READY);
                        }
                    }
                }
            }
            
            response.put("members", getMembers(gid,0));            

            g.setVersion(Storage.saveEntity(g).getVersion());
            
            valid = true;
        }

        response.put("valid", valid);

        if(broadcastAppInfo)    broadcastAppEventInfo(socket, g, "group_updated", true);
        if(broadcastGroupInfo)  socket.publishEvent(WampServices.getTopic(getFQtopicURI("group_event:"+g.getGid())), null, response, true, false);  // exclude Me
        return response;
    }
    
    
    @WampRPC(name="list_members")
    public WampList getMembers(String gid, int team) throws Exception 
    {
        WampList membersArray = new WampList();

        Group g = groups.get(gid);
        if(g != null) {
            for(int slot = 0, numSlots = g.getNumSlots(); slot < numSlots; slot++) {
                Member member = g.getMember(slot);
                if( (member != null) && (team==0 || team==member.getTeam()) ) {
                    WampDict obj = member.toWampObject();
                    membersArray.add(obj);
                }
            }
        }
        return membersArray;        
    }
    
    
    @WampRPC(name="update_member")
    public WampDict updateMember(WampSocket socket, WampDict data) throws Exception
    {
            boolean valid = false;
            String gid = data.getText("gid");

            WampDict response = new WampDict();
            response.put("cmd", "group_updated");
            response.put("sid", socket.getSessionId());

            Group g = groups.get(gid);
            if(g != null) {
                logger.log(Level.FINE, "open_group: group found: " + gid);
                
                response.putAll(g.toWampObject());
                if(data.has("slot")) {
                    
                    // UPDATE MEMBER SLOT
                    int slot = data.getLong("slot").intValue();
                    if(slot < 0) {
                        // TODO: check client socket is allowed to remove slot when index < 0
                        WampList membersArray = new WampList();
                        Storage.removeEntity(g.removeMember(-slot-1));
                        
                        slot = 0;
                        for(int numSlots = g.getNumSlots(); slot < numSlots; slot++) {
                            Member member = g.getMember(slot);
                            if(member != null) {
                                WampDict obj = new WampDict();
                                membersArray.add(member.toWampObject());
                            }
                        }
                        response.put("members", membersArray);
                        
                        valid = true;
                    }
                    else {
                        String userId = data.getText("user");
                        String role = data.getText("role");
                        String usertype = data.getText("type");
                        int team = data.getLong("team").intValue();

                        Client c = clients.get(data.getLong("sid"));
                        if(c!=null) {
                            // when it's not a reservation of a member slot
                            User u = c.getUser();
                            if(u!=null) userId = u.getFQid();
                        }

                        Role r = g.getApplication().getRoleByName(role);

                        // TODO: check "slot" is valid
                        EntityManager manager = Storage.getEntityManager();
                        User user = manager.find(User.class, new UserId(userId));
                        manager.close();

                        Member member = g.getMember(slot);
                        if(member == null) {
                            member = new Member();
                            member.setApplicationGroup(g);
                            member.setSlot(slot);
                            member.setTeam(1+slot);
                            member.setUserType("user");
                        }

                        if(c==null) member.setState((g.getState() == GroupState.OPEN)? MemberState.EMPTY : MemberState.DETACHED );
                        else if(c != member.getClient()) member.setState(MemberState.JOINED);

                        if(usertype.equalsIgnoreCase("remote")) {
                            if(user!=null && user.equals(member.getUser())) {
                                usertype = member.getUserType();
                            } else {
                                usertype = "user";  // by default, but try to maintain remote's usertype selection
                                for(int index = 0, numSlots = g.getNumSlots(); index < numSlots; index++) {
                                    Member m2 = g.getMember(index);
                                    if(user.equals(m2.getUser())) {
                                        usertype = m2.getUserType();
                                        break;
                                    }
                                }
                            }
                        }

                        member.setClient(c);
                        member.setUser(user);
                        member.setUserType(usertype);
                        member.setRole(r);
                        member.setTeam(team);
                        g.setMember(slot, member);

                        response.putAll(member.toWampObject());
                        valid = true;
                        
                    } 
                    
                } else {
                    // UPDATE CLIENT STATE ("joined" <--> "ready")
                    Long sid = socket.getSessionId();
                    WampList membersArray = new WampList();
                    String state = data.getText("state");
                    if(state != null) {
                        for(int slot = 0, numSlots = g.getNumSlots(); slot < numSlots; slot++) {
                            Member member = g.getMember(slot);
                            if( (member != null) && (member.getClient() != null) && (member.getClient().getSessionId().equals(sid)) ) {
                                member.setState(MemberState.valueOf(state));
                            }
                            WampDict obj = member.toWampObject();
                            membersArray.add(obj);
                        }
                        response.put("members", membersArray);
                    }
                    valid = true;
                }
            }

            response.put("valid", valid);

            if(valid) {
                //response.putAll(g.toJSON());
                broadcastAppEventInfo(socket, g, "group_updated", false);  // exclude Me
                socket.publishEvent(WampServices.getTopic(getFQtopicURI("group_event:"+g.getGid())), null, response, false, false);
                g.setVersion(Storage.saveEntity(g).getVersion());
            }  
            return response;
    }
    

    @WampRPC(name="send_group_message")
    public void sendGroupMessage(WampSocket socket, String gid, WampObject data) throws Exception
    {
        Group g = groups.get(gid);
        if(g != null) {
            WampDict event = new WampDict();
            event.put("cmd", "group_message");
            event.put("message", data);
            socket.publishEvent(WampServices.getTopic(getFQtopicURI("group_event:"+gid)), null, event, false, true); // don't exclude Me
        }
    }
    
    @WampRPC(name="send_team_message")
    public void sendTeamMessage(WampSocket socket, String gid, WampObject data) throws Exception
    {
        Group g = groups.get(gid);
        if(g != null) {
            int team = 0;
        
            // Search team of caller
            for(int slot = 0, numSlots = g.getNumSlots(); slot < numSlots; slot++) {
                Member member = g.getMember(slot);
                if(member != null) {
                    Client c = member.getClient();
                    if( (c != null) && (socket.getSessionId().equals(c.getSessionId())) ) {
                        team = slot;
                        break;
                    }
                }
            }        

            if(team != 0) {
                WampDict event = new WampDict();
                event.put("cmd", "team_message");
                event.put("message", data); 
                
                Set<Long> eligibleSet = new HashSet<Long>();

                for(int slot = 0, numSlots = g.getNumSlots(); slot < numSlots; slot++) {
                    Member member = g.getMember(slot);
                    if( (member != null) && (member.getTeam() == team) ) {
                        Client c = member.getClient();
                        if(c != null) eligibleSet.add(c.getSessionId());
                    }
                }

                if(eligibleSet.size() > 0) {
                    WampPublishOptions options = new WampPublishOptions();
                    options.setEligible(eligibleSet);
                    options.setDiscloseMe(true);
                    WampServices.publishEvent(WampProtocol.newId(), socket.getSessionId(), WampServices.getTopic(getFQtopicURI("group_event:"+g.getGid())), null, event, options);
                }
            }
        }
    }
    
    @WampRPC(name="exit_group")
    public WampDict exitGroup(WampSocket socket, String gid) throws Exception
    {
            Client client = clients.get(socket.getSessionId());
            
            WampDict response = new WampDict();
            response.put("cmd", "user_detached");
            response.put("gid", gid);
            response.put("valid", "false");

            Group g = groups.get(gid);

            if(g != null) {
                String appId = g.getApplication().getAppId();
                logger.log(Level.FINE, "open_group: group found: " + gid);

                response.put("valid", true);
                response.put("sid", socket.getSessionId());

                int num_members = 0;
                WampList membersArray = new WampList();
                for(int slot = g.getNumSlots(); slot > 0; ) {
                    slot = slot-1;
                    Member member = g.getMember(slot);
                    boolean connected = (member!=null && member.getClient() != null);
                    if(connected) {
                        if(client == member.getClient()) {
                            logger.log(Level.INFO, "clearing slot " + slot);

                            member.setClient(null);
                            if(g.getState() == GroupState.OPEN) {
                                member.setState(MemberState.EMPTY);
                                member.setUser(null);
                                member.setUserType("user");
                            } else {
                                member.setState(MemberState.DETACHED);
                            }
                            g.setMember(slot, member);
                            
                            if(g.isAutoMatchEnabled() && g.isAutoMatchCompleted()) {
                                g.setAutoMatchCompleted(false);
                            }                            
                            
                            WampDict obj = member.toWampObject();
                            membersArray.add(obj);
                            
                            g.setVersion(Storage.saveEntity(g).getVersion());

                        } else {
                            num_members++;
                        }
                    }
                }
                response.put("members", membersArray);

                socket.publishEvent(WampServices.getTopic(getFQtopicURI("group_event:"+gid)), null, response, true, false); // exclude Me

                client.removeGroup(g);
                
                String topicName = getFQtopicURI("group_event:" + g.getGid());

                WampTopic topic = WampServices.getTopic(topicName);
                for(WampSubscription subscription : topic.getSubscriptions()) {
                    if(subscription.removeSocket(socket.getSessionId()) != null) {
                        boolean deleted = false;
                        if(subscription.getSocketsCount() == 0 && g.getState() != GroupState.STARTED) {

                            switch(g.getState()) {
                                case OPEN:
                                    Storage.removeEntity(g);
                                    break;
                                case FINISHED:
                                    // move group to historic table?
                                    break;
                            }

                            logger.log(Level.INFO, "closing group {0}: {1}", new Object[]{ g.getGid(), g.getDescription()});

                            groups.remove(g.getGid());
                            applications.get(appId).removeGroup(g);

                            //updateAppInfo(socket, applications.get(appId), "app_updated", false);

                            WampServices.removeTopic(wampApp, topicName);
                            deleted = true;
                        }

                        broadcastAppEventInfo(socket, g, deleted? "group_deleted" : "group_updated", false);
                    }
                }
                
            }

            return response;
    }

    
    private void broadcastAppEventInfo(WampSocket socket, Group g, String cmd, boolean excludeMe) throws Exception
    {
        WampDict event = g.toWampObject();
        event.put("cmd", cmd);
        event.put("members", getMembers(g.getGid(),0));
        socket.publishEvent(WampServices.getTopic(getFQtopicURI("app_event:"+g.getApplication().getAppId())), null, event, excludeMe, false);
    }
    
    private void broadcastGroupEventInfo(WampSocket socket, Group g, String cmd, boolean excludeMe) throws Exception
    {
        WampDict event = g.toWampObject();
        event.put("cmd", cmd);
        event.put("members", getMembers(g.getGid(),0));
        socket.publishEvent(WampServices.getTopic(getFQtopicURI("group_event:"+g.getGid())), null, event, excludeMe, false);
    }    
    
    
    private WampDict listGroups(WampSocket socket, String appId, GroupFilter options) throws Exception
    {
        System.out.println("Listing groups for app: '" + appId + "'");
        Client client = clients.get(socket.getSessionId());
        WampDict retval = new WampDict();
        WampList groupsArray = new WampList();
        Application app = applications.get(appId);
        if(app != null) {
            retval.put("app", app.toWampObject());
            for(Group group : app.getGroupsByState(null)) {
                if(!group.isHidden() && options.subscribeGroup(group, client)) {
                    WampDict obj = group.toWampObject();
                    obj.put("members", getMembers(group.getGid(),0));                
                    groupsArray.add(obj);
                }
            }   
        } else {
            retval.put("app", "*");
            for(Group group : groups.values()) {
                if(!group.isHidden() && options.subscribeGroup(group, client)) {
                    WampDict obj = group.toWampObject();
                    obj.put("members", getMembers(group.getGid(),0));                
                    groupsArray.add(obj);
                }
            }               
        }
        
        retval.put("groups", groupsArray);

        return retval;
    }    

    
    @WampRPC(name = "get_leaderboard")
    public WampList getLeaderBoard(WampSocket socket, String appId, int leaderBoardId)
    {
        WampList scoresNode = new WampList();
        Application app = applications.get(appId);
        
        LeaderBoard leaderBoard = new LeaderBoard();
        for(LeaderBoard ld : app.getLeaderBoards()) {
            if(leaderBoardId == ld.getId()) {
                leaderBoard = ld;
                break;
            }
        }
        
        for(Score score : leaderBoard.getScores()) {
            if(score != null) {
                scoresNode.add(score.toWampObject());
            }
        }

        return scoresNode;
    }
    
    
    @WampRPC(name = "add_score")
    public int addScore(WampSocket socket, String appId, int leaderBoardId, BigDecimal value)
    {
        Application app = applications.get(appId);
        Calendar time = Calendar.getInstance();
        Client client = clients.get(socket.getSessionId());
        User usr = (client != null) ? client.getUser() : null;
        
        int position = -1;
        if(app != null && value != null) {
        
            LeaderBoard leaderBoard = null;
            for(LeaderBoard ld : app.getLeaderBoards()) {
                if(leaderBoardId == ld.getId()) {
                    leaderBoard = ld;
                    break;
                }
            }
            
            if(leaderBoard == null) {
                leaderBoard = new LeaderBoard();
                leaderBoard.setApplication(app);
                leaderBoard.setId(leaderBoardId);
            }
            
            
            BigDecimal factor = BigDecimal.ONE;
            if(app.isDescendingScoreOrder()) factor = factor.negate();

            Score score = null;
            int index = app.getMaxScores();
            while(index > 1) {
                score = leaderBoard.getScore(index-1);
                if(score == null || score.getValue().compareTo(value) < 0) {
                    index--;
                    leaderBoard.setScore(index, leaderBoard.getScore(index-1));
                } else {
                    break;
                }
            }      
            
            if( index < app.getMaxScores() 
                    && (score == null || score.getValue().multiply(factor).compareTo(value) < 0) )  {
                score = new Score();
                score.setPosition(index);
                score.setUser(usr);
                score.setTime(time);
                score.setLeaderBoard(leaderBoard);
                score.setValue(value);
                leaderBoard.setScore(index, score);

                Storage.saveEntity(leaderBoard);
                
                position = index;
            }
        
        }
        
        return position;
    }    

}
