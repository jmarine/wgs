/**
 * WebSocket Game services implementation
 *
 * @author Jordi Marine Fort 
 */

package org.wampservices.wgs;


import org.wampservices.util.Storage;
import org.wampservices.entity.User;
import org.wampservices.entity.UserId;
import org.wampservices.WampConnectionState;
import org.wampservices.entity.OpenIdConnectClient;
import org.wampservices.util.Base64;
import org.wampservices.WampApplication;
import org.wampservices.WampException;
import org.wampservices.WampMetaTopic;
import org.wampservices.WampModule;
import org.wampservices.WampRPC;
import org.wampservices.WampSocket;
import org.wampservices.WampSubscription;
import org.wampservices.WampSubscriptionOptions;
import org.wampservices.WampTopic;
import org.wampservices.WampTopicOptions;
import org.wampservices.entity.OpenIdConnectClientPK;
import org.wampservices.entity.OpenIdConnectProvider;
import java.net.URL;
import java.net.URLEncoder;
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
import javax.persistence.TypedQuery;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.wampservices.WampCallOptions;
import org.wampservices.WampPublishOptions;


public class Module extends WampModule 
{
    private static final Logger logger = Logger.getLogger(Module.class.toString());
    private static final String MODULE_URL = WampApplication.WAMPSERVICES_BASE_URL + "/wgs#";
    
    private WampApplication wampApp = null;
    private Map<String,Client> clients = new ConcurrentHashMap<String,Client>();
    private Map<String, Application> applications = new ConcurrentHashMap<String,Application>();
    private Map<String, Group> groups = new ConcurrentHashMap<String,Group>();
    
    public Module(WampApplication app)
    {
        super(app);
        this.wampApp = app;
        
        wampApp.createTopic(getFQtopicURI("apps_event"), null);

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

    
    @Override
    public String getBaseURL() {
        return MODULE_URL;
    }
    
    private String getFQtopicURI(String topicName)
    {
        return MODULE_URL + topicName;
    }

    @Override
    public Object onCall(WampSocket socket, String method, ArrayNode args, WampCallOptions callOptions) throws Exception 
    {
        Object retval = null;
        if(method.equals("list_groups")) {
            String appId = args.get(0).asText();
            WampSubscriptionOptions options = new WampSubscriptionOptions(null);
            options.setMetaEvents(java.util.Arrays.asList("http://wamp.ws/sub#joined", "http://wamp.ws/sub#left"));
             
            wampApp.subscribeClientWithTopic(socket, getFQtopicURI("app_event:"+appId), options);
            retval = listGroups(appId);
        } else {
            retval = super.onCall(socket, method, args, callOptions);
        }
        return retval;
    }

    
    /*
    @Override
    public void onSubscribe(WampSocket clientSocket, WampTopic topic, WampSubscriptionOptions options) throws Exception { 
        super.onSubscribe(clientSocket, topic, options);
        
        if(options != null && options.isMetaEventsEnabled()) {
            ObjectMapper mapper = new ObjectMapper();
            ArrayNode arrayNode = mapper.createArrayNode();
            String metatopic = MODULE_URL + "other_subscriptions";
            
            for(String sid : topic.getSessionIds()) {
                if(!sid.equals(clientSocket.getSessionId())) {
                    WampSubscription presence = topic.getSubscription(sid);
                    ObjectNode obj = presence.toJSON();
                    Client cli = clients.get(presence.getSocket().getSessionId());
                    if(cli != null && cli.getState()==ConnectionState.AUTHENTICATED) {
                        obj.putAll(cli.getUser().toJSON());
                        arrayNode.add(obj);
                    }                
                }
            }        
            
            if(arrayNode.size() > 0) {
                wampApp.publishMetaEvent(topic, metatopic, arrayNode, clientSocket);
            }
        }
        
    }
    
    @Override
    public void onMetaEvent(WampTopic topic, String metatopic, JsonNode metaevent, WampSocket toClient) throws Exception 
    {
        if(metatopic.equals(WampMetaTopic.JOINED)) {        
            ObjectNode obj = (ObjectNode)metaevent;
            Client cli = clients.get(obj.get("sessionId").asText());
            if(cli != null && cli.getState()==ConnectionState.AUTHENTICATED) {
                obj.putAll(cli.getUser().toJSON());
            }
        }
        super.onMetaEvent(topic, metatopic, metaevent, toClient);
    }
    */

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
        
        wampApp.subscribeClientWithTopic(socket, getFQtopicURI("apps_event"), null);  // exact match
    }
    
    @Override
    public void onDisconnect(WampSocket socket) throws Exception {
        Client client = clients.get(socket.getSessionId());
        wampApp.unsubscribeClientFromTopic(socket, getFQtopicURI("apps_event"), null);  // exact match
        socket.setState(WampConnectionState.OFFLINE);
        for(String gid : client.getGroups().keySet()) {
            exitGroup(socket, gid);
        }
        clients.remove(socket.getSessionId());
        super.onDisconnect(socket);
    }
    
    @WampRPC(name="set_subscription_status")
    public void setSubscriptionStatus(WampSocket socket, String topicName, JsonNode newStatus) throws Exception
    {
        WampTopic topic = wampApp.getTopic(socket.normalizeURI(topicName));
        if(topic != null) {
            WampSubscription subscription = topic.getSubscription(socket.getSessionId());
            if(subscription != null) {
                subscription.setStatus(newStatus);
                String metatopic = MODULE_URL + "status_updated";
                wampApp.publishMetaEvent(topic, metatopic, subscription.toJSON(), null);
            }
        }
    }
    
    @WampRPC(name="register")
    public ObjectNode registerUser(WampSocket socket, ObjectNode data) throws Exception
    {
        boolean user_valid = false;
        User usr = null;
        
        Client client = clients.get(socket.getSessionId());

        String user = data.get("user").asText();
        UserId userId = new UserId(User.LOCAL_USER_DOMAIN, user);
        
        EntityManager manager = Storage.getEntityManager();
        usr = manager.find(User.class, userId);
        manager.close();
        
        if(usr != null) throw new WampException(MODULE_URL + "userexists", "The user is reserved by another user");
        
        usr = new User();
        usr.setProfileCaducity(null);
        usr.setId(userId);
        if(user.length() == 0) usr.setName("");
        else usr.setName(Character.toUpperCase(user.charAt(0)) + user.substring(1));
        usr.setPassword(data.get("password").asText());
        usr.setEmail(data.get("email").asText());
        usr.setAdministrator(false);
        usr = Storage.saveEntity(usr);

        socket.setUserPrincipal(usr);
        socket.setState(WampConnectionState.AUTHENTICATED);
        
        return usr.toJSON();
    }
    
    
    @WampRPC(name="get_user_info")
    public ObjectNode getUserInfo(WampSocket socket, ObjectNode data) throws Exception
    {
        boolean user_valid = false;
        Client client = clients.get(socket.getSessionId());

        if(client == null || client.getSocket().getState() != WampConnectionState.AUTHENTICATED) {
            throw new WampException(MODULE_URL + "anonymous-user", "User not authenticated");
        }
        
        User usr = client.getUser();
        if(usr == null) {
            throw new WampException(MODULE_URL + "unknown-user-info", "User info not registered");
        }
        
        return usr.toJSON();
    }

    
    @WampRPC(name="openid_connect_providers")
    public ObjectNode openIdConnectProviders(WampSocket socket, ObjectNode data) throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode retval = mapper.createObjectNode();
        ArrayNode providers = mapper.createArrayNode();
        String redirectUri = data.get("redirect_uri").asText();
        redirectUri = redirectUri + ((redirectUri.indexOf("?") == -1)?"?":"&") + "provider=%";
        
        Calendar now = Calendar.getInstance();        
        EntityManager manager = null;
        ArrayList<String> domains = new ArrayList<String>();
        
        try {
            manager = Storage.getEntityManager();

            TypedQuery<OpenIdConnectClient> queryClients = manager.createNamedQuery("oic_client.findByRedirectUri", OpenIdConnectClient.class);
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
                    String uri = oicAuthEndpointUrl + "?response_type=code&scope=openid%20profile%20email&client_id=" + URLEncoder.encode(clientId,"utf8") + "&redirect_uri=" + URLEncoder.encode(oic.getRedirectUri(),"utf8");

                    ObjectNode node = mapper.createObjectNode();
                    node.put("name", providerDomain);
                    node.put("authEndpoint", uri);
                    providers.add(node);
                    domains.add(providerDomain);
                }
            }
            
            TypedQuery<OpenIdConnectProvider> queryProviders = manager.createNamedQuery("oic_provider.findDynamic", OpenIdConnectProvider.class);
            for(OpenIdConnectProvider provider : queryProviders.getResultList()) {
                String domain = provider.getDomain();
                if(!domains.contains(domain) && !"defaultProvider".equals(domain)) {
                    ObjectNode node = mapper.createObjectNode();
                    node.put("name", domain);
                    node.put("registrationEndpoint", provider.getRegistrationEndpointUrl());
                    providers.add(node);
                    domains.add(domain);
                }
            }

            retval.put("providers", providers);
            
        } catch(Exception ex) {
            
            throw new WampException(MODULE_URL + "oic_error", "OpenID Connect provider error: " + ex.getMessage());
        
        } finally {
            if(manager != null) {
                try { manager.close(); }
                catch(Exception ex) { }
            }            
        }
        
        return retval;
    }
            
    
    @WampRPC(name="openid_connect_login_url")
    public String openIdConnectLoginUrl(WampSocket socket, ObjectNode data) throws Exception
    {
        String retval = null;
        String providerDomain = null;
        String principal = data.get("principal").asText();
        
        if(principal == null || principal.length() == 0) {
            providerDomain = "defaultProvider";            
        } else {
            try { 
                String normalizedIdentityURL = principal;
                if(normalizedIdentityURL.indexOf("://") == -1) normalizedIdentityURL = "https://" + principal;
                URL url = new URL(normalizedIdentityURL); 
                providerDomain = url.getHost();
            } finally {
                if(providerDomain == null) throw new WampException(MODULE_URL + "oic_error", "Unsupported OpenID Connect principal format");
            }
        }
        
        
        String redirectUri = data.get("redirect_uri").asText();
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
                retval = oicAuthEndpointUrl + "?response_type=code&scope=openid%20profile%20email&client_id=" + URLEncoder.encode(clientId,"utf8") + "&redirect_uri=" + URLEncoder.encode(redirectUri,"utf8");
            }
            
            if(retval == null) {
                throw new Exception("Unknown provider for domain " + providerDomain);
            }
            
        } catch(Exception ex) {
            
            throw new WampException(MODULE_URL + "oic_error", "OpenID Connect provider error: " + ex.getMessage());
        
        } finally {
            if(manager != null) {
                try { manager.close(); }
                catch(Exception ex) { }
            }            
        }
        
        return retval;
    }
    
    @WampRPC(name="openid_connect_auth")
    public ObjectNode openIdConnectAuth(WampSocket socket, ObjectNode data) throws Exception
    {
        User usr = null;
        EntityManager manager = null;
        Client client = clients.get(socket.getSessionId());

        try {
            String code = data.get("code").asText();
            String providerDomain = data.get("provider").asText();
            String redirectUri = data.get("redirect_uri").asText();
            
            manager = Storage.getEntityManager();
            OpenIdConnectClientPK oicId = new OpenIdConnectClientPK(providerDomain, redirectUri);
            OpenIdConnectClient oic = manager.find(OpenIdConnectClient.class, oicId);
            if(oic == null) throw new WampException(MODULE_URL + "unknown_oic_provider", "Unknown OpenId Connect provider domain");

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

        if(usr == null) throw new WampException(MODULE_URL + "oic_error", "OpenID Connect protocol error");
        return usr.toJSON();
    }    
    
    
    
    @WampRPC(name="list_apps")
    public ObjectNode listApps() throws Exception
    {
        // TODO: Filter by domain
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode retval = mapper.createObjectNode();
        ArrayNode appArray = mapper.createArrayNode();
        for(Application app : applications.values()) {
            appArray.add(app.toJSON());
        }
        retval.put("apps", appArray);

        return retval;
    }
    
    
    private void registerApplication(Application app) {
        wampApp.createTopic(getFQtopicURI("app_event:"+app.getAppId()), null);
        applications.put(app.getAppId(), app);
    }
    
    private void unregisterApplication(Application app) {
        wampApp.removeTopic(getFQtopicURI("app_event:"+app.getAppId()));
        applications.remove(app.getAppId());
    }
    

    @WampRPC(name="new_app")
    public ObjectNode newApp(WampSocket socket, ObjectNode data) throws Exception
    {
        // TODO: check it doesn't exists

        boolean valid = false;
        Client client = clients.get(socket.getSessionId());
        if(socket.getState() != WampConnectionState.AUTHENTICATED) throw new WampException(MODULE_URL + "unknownuser", "The user hasn't logged in");
        
        // TODO: check user is administrator
        //if(!client.getUser().isAdministrator()) throw new WampException(MODULE_URL + "adminrequired", "The user is not and administrator");
        
        Application app = new Application();
        app.setAppId(UUID.randomUUID().toString());
        app.setAdminUser(client.getUser());
        app.setName(data.get("name").asText());
        app.setDomain(data.get("domain").asText());
        app.setVersion(data.get("version").asInt());
        app.setMaxMembers(data.get("max").asInt());
        app.setMinMembers(data.get("min").asInt());
        app.setDeltaMembers(data.get("delta").asInt());
        app.setAlliancesAllowed(data.get("alliances").asBoolean());
        app.setDynamicGroup(data.get("dynamic").asBoolean());
        app.setObservableGroup(data.get("observable").asBoolean());
        app.setAIavailable(data.get("ai_available").asBoolean());

        ArrayNode roles = (ArrayNode)data.get("roles");
        for(int i = 0; i < roles.size(); i++) {
            String roleName = roles.get(i).asText();
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

        ObjectNode event = broadcastAppInfo(socket, app, "app_created", true);
        return event;
    }
        
    
    @WampRPC(name="delete_app")
    public ObjectNode deleteApp(WampSocket socket, ObjectNode param) throws Exception
    {
        // TODO: check user is administrator of app
        // TODO: delete groups
        
        ObjectNode event = null;
        String appId = param.get("app").asText();

        Application app = applications.get(appId);
        if(app != null) {
            Storage.removeEntity(app);
            unregisterApplication(app);
            event = broadcastAppInfo(socket, app, "app_deleted", true);
            return event;
        } else {
            throw new WampException(Module.MODULE_URL + "#appidnotfound", "AppId " + appId + " doesn't exist");
        }
    }
    
    
    private ObjectNode broadcastAppInfo(WampSocket socket, Application app, String cmd, boolean excludeMe) throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode event = app.toJSON();
        event.put("cmd", cmd);
        socket.publishEvent(wampApp.getTopic(getFQtopicURI("apps_event")), event, excludeMe, false);
        return event;
    }

    
    @WampRPC(name="open_group")
    public synchronized ObjectNode openGroup(WampSocket socket, String appId, String gid, ObjectNode options) throws Exception
    {
        Group   g = null;
        boolean valid   = false;
        boolean created = false;
        boolean joined  = false;
        boolean autoMatchMode = false;
        boolean spectator = false;
        if( (options != null) && (options.has("spectator")) ) {
            spectator = options.get("spectator").asBoolean(false);
        }
        
        Client client = clients.get(socket.getSessionId());
        
        if(gid != null) {
            if(gid.equals("automatch")) {
                Application app = applications.get(appId);
                if(app != null) {
                    autoMatchMode = true;
                    g = app.getNextAutoMatchGroup();
                    if(g != null) valid = true;
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
                String pwd2 = (options!=null && options.has("password"))? options.get("password").asText() : "";
                if(!pwd.equals(pwd2)) throw new WampException(MODULE_URL + "incorrectpassword", "Incorrect password");
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
                        autoMatchMode = options.get("automatch").asBoolean();
                        g.setAutoMatchEnabled(autoMatchMode);
                    } 
                    if(options.has("hidden")) {
                        g.setHidden(options.get("hidden").asBoolean(false));
                    }
                    if(options.has("observable")) {
                        g.setObservableGroup(options.get("observable").asBoolean(g.getApplication().isObservableGroup()));
                    }                    
                    if(!autoMatchMode && options.has("password")) {
                        String password = options.get("password").asText();
                        g.setPassword( (password!=null && password.length()>0)? password : null);
                    }
                    if(options.has("description")) {
                        g.setDescription(options.get("description").asText());
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
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode response = (g!=null)? g.toJSON() : mapper.createObjectNode();
        response.put("cmd", "user_joined");

        if(valid) {
            Application app = g.getApplication();
            ArrayList<String> requiredRoles = new ArrayList<String>();
            for(Role r : app.getRoles()) {
                if(r.isRequired()) requiredRoles.add(r.getName());
            }
            
            response.put("created", created);
            response.put("app", app.toJSON());

            String topicName = getFQtopicURI("group_event:" + g.getGid());
            WampTopic topic = wampApp.getTopic(topicName);
            if(topic == null) {
                WampTopicOptions topicOptions = new WampTopicOptions();
                topic = wampApp.createTopic(topicName, topicOptions);
            }
            WampSubscriptionOptions subscriptionOptions = new WampSubscriptionOptions(null);
            //subscriptionOptions.setPublisherIdRequested(true);
            wampApp.subscribeClientWithTopic(client.getSocket(), topicName, subscriptionOptions);
            
            client.addGroup(g);
            ArrayNode conArray = mapper.createArrayNode();
            for(String sid : topic.getSessionIds()) {
                    Client c = clients.get(sid);
                    User u = ((c!=null)? c.getUser() : null);
                    String user = ((u == null) ? "" : u.getFQid());
                    String name = ((u == null) ? "" : u.getName());
                    String picture = ((u == null) ? null : u.getPicture());

                    ObjectNode con = mapper.createObjectNode();
                    con.put("user", user);
                    con.put("name", name);
                    con.put("picture", picture);
                    con.put("sid", sid);
                    conArray.add(con);
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

            int requiredSlot = (options != null && options.has("slot"))? options.get("slot").asInt() : -1;
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
                        String roleName = options.get("role").asText();
                        role = g.getApplication().getRoleByName(roleName);
                        if(role != null && (oldRole == null || !roleName.equals(oldRole.getName())) ) {
                            requiredRoles.remove(roleName);
                            if(oldRole != null && oldRole.isRequired()) requiredRoles.add(oldRole.getName());
                            member.setRole(role);
                        }
                    }                    

                    joined = true;
                    connected = true;

                    ObjectNode event = member.toJSON();
                    event.put("cmd", "user_joined");
                    //event.put("sid", client.getSessionId());
                    //event.put("user", member.getUser().getFQid());
                    //event.put("name", member.getUser().getName());
                    //event.put("picture", member.getUser().getPicture());
                    event.put("gid", g.getGid());
                    event.put("valid", true);

                    socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+g.getGid())), event, true, false);  // exclude Me
                }

            }

            response.put("members", getMembers(g.getGid(), 0));
            
            broadcastAppEventInfo(socket, g, created? "group_created" : "group_updated", false);
            
            Storage.saveEntity(g);

        }

        
        if(valid && !created && !joined) {
            User u = client.getUser();
            String sid = client.getSessionId();
            String user = ( (u == null) ? "" : u.getFQid() );

            ObjectNode event = mapper.createObjectNode();
            event.put("cmd", "user_joined");
            event.put("gid", g.getGid());
            event.put("user", user);
            event.put("name", ((u == null)? "" : u.getName()) );
            event.put("picture", ((u == null)? null : u.getPicture()) );
            event.put("sid", sid);
            event.put("type", "user");
            event.put("valid", valid);
                    
            socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+g.getGid())), event, true, false);  // exclude Me
        }
        
        return response;
    }
    

    @WampRPC(name="update_group")
    public ObjectNode updateGroup(WampSocket socket, ObjectNode node) throws Exception
    {
        // TODO: change group properties (state, observable, etc)

        boolean valid = false;
        boolean broadcastAppInfo = false;
        boolean broadcastGroupInfo = false;
        String appId = node.get("app").asText();
        String gid = node.get("gid").asText();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode response = mapper.createObjectNode();
        response.put("cmd", "group_updated");
        response.put("sid", socket.getSessionId());
        
        
        Group g = groups.get(gid);
        if(g != null) {
            logger.log(Level.FINE, "open_group: group found: " + gid);
            
            if(node.has("automatch")) {
                boolean autoMatchMode = node.get("automatch").asBoolean();
                g.setAutoMatchEnabled(autoMatchMode);
                g.getApplication().addAutoMatchGroup(g);
                broadcastGroupInfo = true;
            } 

            if(node.has("dynamic")) {
                boolean dynamic = node.get("dynamic").asBoolean();
                g.setDynamicGroup(dynamic);
                broadcastGroupInfo = true;
            }
            
            if(node.has("alliances")) {
                boolean alliances = node.get("alliances").asBoolean();
                g.setAlliancesAllowed(alliances);
                broadcastGroupInfo = true;
            }            

            if(node.has("hidden")) {
                boolean hidden = node.get("hidden").asBoolean();
                g.setHidden(hidden);
                broadcastAppInfo = true;
                broadcastGroupInfo = true;
            }            
            
            if(node.has("observable")) {
                boolean observable = node.get("observable").asBoolean();
                g.setObservableGroup(observable);
                broadcastAppInfo = true;
                broadcastGroupInfo = true;
            }                                 
            
            if(node.has("data")) {
                String data = node.get("data").asText();
                g.setData(data);
                broadcastGroupInfo = true;
            }
            
            if(node.has("state")) {
                String state = node.get("state").asText();
                g.setState(GroupState.valueOf(state));
                broadcastAppInfo = true;
                broadcastGroupInfo = true;                
            }

            
            response.putAll(g.toJSON());
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

            Storage.saveEntity(g);
            
            valid = true;
        }

        response.put("valid", valid);

        if(broadcastAppInfo)    broadcastAppEventInfo(socket, g, "group_updated", true);
        if(broadcastGroupInfo)  socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+g.getGid())), response, true, false);  // exclude Me
        return response;
    }
    
    
    @WampRPC(name="list_members")
    public ArrayNode getMembers(String gid, int team) throws Exception 
    {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode membersArray = mapper.createArrayNode();

        Group g = groups.get(gid);
        if(g != null) {
            for(int slot = 0, numSlots = g.getNumSlots(); slot < numSlots; slot++) {
                Member member = g.getMember(slot);
                if( (member != null) && (team==0 || team==member.getTeam()) ) {
                    ObjectNode obj = member.toJSON();
                    membersArray.add(obj);
                }
            }
        }
        return membersArray;        
    }
    
    
    @WampRPC(name="update_member")
    public ObjectNode updateMember(WampSocket socket, ObjectNode data) throws Exception
    {
            boolean valid = false;
            String gid = data.get("gid").asText();

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode response = mapper.createObjectNode();
            response.put("cmd", "group_updated");
            response.put("sid", socket.getSessionId());

            Group g = groups.get(gid);
            if(g != null) {
                logger.log(Level.FINE, "open_group: group found: " + gid);
                
                response.putAll(g.toJSON());
                if(data.has("slot")) {
                    
                    // UPDATE MEMBER SLOT
                    String sid = data.get("sid").asText();
                    
                    int slot = data.get("slot").asInt();
                    if(slot < 0) {
                        // TODO: check client socket is allowed to remove slot when index < 0
                        ArrayNode membersArray = mapper.createArrayNode(); 
                        Storage.removeEntity(g.removeMember(-slot-1));
                        
                        slot = 0;
                        for(int numSlots = g.getNumSlots(); slot < numSlots; slot++) {
                            Member member = g.getMember(slot);
                            if(member != null) {
                                ObjectNode obj = member.toJSON();
                                membersArray.add(member.toJSON());
                            }
                        }
                        response.put("members", membersArray);
                        
                        valid = true;
                    }
                    else {
                        String userId = data.get("user").asText();
                        String role = data.get("role").asText();
                        String usertype = data.get("type").asText();
                        int team = data.get("team").asInt();

                        Client c = clients.get(sid);
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

                        response.putAll(member.toJSON());
                        valid = true;
                        
                    } 
                    
                } else {
                    // UPDATE CLIENT STATE ("joined" <--> "ready")
                    String sid = socket.getSessionId();
                    ArrayNode membersArray = mapper.createArrayNode();
                    JsonNode stateNode = data.get("state");
                    String state = (stateNode!=null && !stateNode.isNull()) ? stateNode.asText() : null;
                    if(state != null) {
                        for(int slot = 0, numSlots = g.getNumSlots(); slot < numSlots; slot++) {
                            Member member = g.getMember(slot);
                            if( (member != null) && (member.getClient() != null) && (member.getClient().getSessionId().equals(sid)) ) {
                                member.setState(MemberState.valueOf(state));
                            }
                            ObjectNode obj = member.toJSON();
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
                socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+g.getGid())), response, false, false);
                Storage.saveEntity(g);
            }  
            return response;
    }
    

    @WampRPC(name="send_group_message")
    public void sendGroupMessage(WampSocket socket, String gid, JsonNode data) throws Exception
    {
        Group g = groups.get(gid);
        if(g != null) {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode event = mapper.createObjectNode();
            event.put("cmd", "group_message");
            event.put("message", data);
            socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+gid)), event, false, true); // don't exclude Me
        }
    }
    
    @WampRPC(name="send_team_message")
    public void sendTeamMessage(WampSocket socket, String gid, JsonNode data) throws Exception
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
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode event = mapper.createObjectNode();
                event.put("cmd", "team_message");
                event.put("message", data); 
                
                Set<String> eligibleSet = new HashSet<String>();

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
                    options.setIdentifyMe(true);
                    wampApp.publishEvent(socket.getSessionId(), wampApp.getTopic(getFQtopicURI("group_event:"+g.getGid())), event, options);
                }
            }
        }
    }
    
    @WampRPC(name="exit_group")
    public ObjectNode exitGroup(WampSocket socket, String gid) throws Exception
    {
            Client client = clients.get(socket.getSessionId());
            
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode response = mapper.createObjectNode();
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
                ArrayNode membersArray = mapper.createArrayNode();
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
                                g.getApplication().addAutoMatchGroup(g);
                            }                            
                            
                            ObjectNode obj = member.toJSON();
                            membersArray.add(obj);
                            
                            Storage.saveEntity(g);

                        } else {
                            num_members++;
                        }
                    }
                }
                response.put("members", membersArray);

                socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+gid)), response, true, false); // exclude Me

                client.removeGroup(g);
                
                String topicName = getFQtopicURI("group_event:" + g.getGid());
                for(WampTopic topic : wampApp.unsubscribeClientFromTopic(socket, topicName, null)) {  // exact match
                    boolean deleted = false;
                    if(topic.getSubscriptionCount() == 0 && g.getState() != GroupState.STARTED) {

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

                        wampApp.removeTopic(topicName);
                        deleted = true;
                    }
                    
                    broadcastAppEventInfo(socket, g, deleted? "group_deleted" : "group_updated", false);
                }
            }

            return response;
    }

    
    private void broadcastAppEventInfo(WampSocket socket, Group g, String cmd, boolean excludeMe) throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode event = g.toJSON();
        event.put("cmd", cmd);
        event.put("members", getMembers(g.getGid(),0));
        socket.publishEvent(wampApp.getTopic(getFQtopicURI("app_event:"+g.getApplication().getAppId())), event, excludeMe, false);
    }
    
    private void broadcastGroupEventInfo(WampSocket socket, Group g, String cmd, boolean excludeMe) throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode event = g.toJSON();
        event.put("cmd", cmd);
        event.put("members", getMembers(g.getGid(),0));
        socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+g.getGid())), event, excludeMe, false);
    }    
    
    
    private ObjectNode listGroups(String appId) throws Exception
    {
        System.out.println("Listing groups for app: '" + appId + "'");

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode retval = mapper.createObjectNode();        
        ArrayNode groupsArray = mapper.createArrayNode();
        Application app = applications.get(appId);
        if(app != null) {
            retval.put("app", app.toJSON());
            for(Group group : app.getGroupsByState(null)) {
                if(group.isHidden()) continue;
                ObjectNode obj = group.toJSON();
                obj.put("members", getMembers(group.getGid(),0));                
                groupsArray.add(obj);
            }   

            retval.put("groups", groupsArray);
        }

        return retval;
    }    

}
