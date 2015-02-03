/**
 * WebSocket Game services implementation
 *
 * @author Jordi Marine Fort 
 */

package org.wgs.service.game;

import java.math.BigDecimal;
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
import org.wgs.util.Storage;
import org.wgs.security.User;
import org.wgs.security.OpenIdConnectUtils;
import org.wgs.security.UserRepository;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.type.WampConnectionState;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.WampException;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.type.WampObject;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.annotation.WampModuleName;
import org.wgs.wamp.annotation.WampRPC;
import org.wgs.security.WampCRA;
import org.wgs.util.Social;
import org.wgs.wamp.topic.WampPublishOptions;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampTopic;
import org.wgs.wamp.topic.WampTopicOptions;


@WampModuleName(Module.WGS_MODULE_NAME)
public class Module extends WampModule 
{
    private static final Logger logger = Logger.getLogger(Module.class.toString());
    public  static final String WGS_MODULE_NAME = "wgs";
    
    private WampApplication wampApp = null;
    private Map<String, Application> applications = new ConcurrentHashMap<String,Application>();
    private Map<String, Group> groups = new ConcurrentHashMap<String,Group>();
    private Map<Long,Client> clients = new ConcurrentHashMap<Long,Client>();

    
    public Module(WampApplication app)
    {
        super(app);
        this.wampApp = app;
        
        WampBroker.createTopic(app, getFQtopicURI("apps_event"), null);

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
    public void onConnect(WampSocket socket) throws Exception 
    {
        super.onConnect(socket);
        Client client = new Client();
        client.setSocket(socket);
        socket.setState(WampConnectionState.ANONYMOUS);
        if(socket.getUserPrincipal() != null) {
            User usr = UserRepository.findUserByLoginAndDomain(socket.getUserPrincipal().getName(), socket.getRealm());
            wampApp.onUserLogon(socket, usr, WampConnectionState.AUTHENTICATED);
        }
        clients.put(socket.getSessionId(), client);
    }
    
    @Override
    public void onDisconnect(WampSocket socket) throws Exception 
    {
        Client client = clients.get(socket.getSessionId());
        if(client != null) {
            for(String gid : client.getGroups().keySet()) {
                exitGroup(socket, gid);
            }
            clients.remove(socket.getSessionId());
        }
        super.onDisconnect(socket);
    }
    

    

    
    @WampRPC(name="register")
    public WampDict registerUser(WampSocket socket, WampDict data) throws Exception
    {
        boolean user_valid = false;
        
        Client client = clients.get(socket.getSessionId());

        String login = data.getText("user");
        User usr = UserRepository.findUserByLoginAndDomain(login, socket.getRealm());
        if(usr != null) throw new WampException(null, WGS_MODULE_NAME + ".user_already_exists", null, null);
        
        usr = new User();
        usr.setProfileCaducity(null);
        usr.setUid(UUID.randomUUID().toString());
        usr.setDomain(socket.getRealm());        
        usr.setLogin(login);
        
        if(login.length() == 0) usr.setName("");
        else usr.setName(Character.toUpperCase(login.charAt(0)) + login.substring(1));
        usr.setPassword(data.getText("password"));
        usr.setEmail(data.getText("email"));
        usr.setAdministrator(false);
        usr.setLastLoginTime(Calendar.getInstance());
        usr = Storage.saveEntity(usr);

        wampApp.onUserLogon(socket, usr, WampConnectionState.AUTHENTICATED);
        
        return usr.toWampObject(true);
    }
    
    
    @WampRPC(name="get_user_info")
    public WampDict getUserInfo(WampSocket socket, WampDict data) throws Exception
    {
        boolean user_valid = false;
        Client client = clients.get(socket.getSessionId());
        
        User usr = client.getUser();
        if(usr == null) {
            usr = new User();
            usr.setUid(UUID.randomUUID().toString());
            usr.setDomain(socket.getRealm());
            usr.setLogin("#anonymous-" + socket.getSessionId());
            usr.setName("Anonymous");
            usr.setPicture("images/anonymous.png");
        }
        
        if(usr == null) {
            return null;
        } else {
            return usr.toWampObject(true);
        }
        
    }

    
    @WampRPC(name="openid_connect_login_url")
    public String openIdConnectLoginUrl(WampSocket socket, WampDict data) throws Exception
    {
        String clientName = data.getText("_oauth2_client_name");
        String redirectUri = data.getText("_oauth2_redirect_uri");
        String subject = data.getText("_oauth2_subject");
        String state = data.has("_oauth2_state")? data.getText("_oauth2_state") : null;
        return OpenIdConnectUtils.getAuthURL(clientName, redirectUri, subject, state);
    }
    
    
    @WampRPC(name="openid_connect_auth")
    public WampDict openIdConnectAuth(WampSocket socket, WampDict data) throws Exception
    {
        WampDict retval = null;

        try {
            String code = data.getText("auth_code");
            retval = OpenIdConnectUtils.verifyCodeFlow(wampApp, socket, code, data);
            
        } catch(Exception ex) {
            retval = null;
            logger.log(Level.SEVERE, "OpenID Connect error: " + ex.getClass().getName() + ":" + ex.getMessage(), ex);
        }

        if(retval == null) {
            System.err.println("OpenID Connect protocol error");
            throw new WampException(null, WGS_MODULE_NAME + ".oidc_error", null, null);
        }
        return retval;
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
        WampBroker.createTopic(wampApp, getFQtopicURI("app_event."+app.getAppId()), null);
        applications.put(app.getAppId(), app);
    }
    
    private void unregisterApplication(Application app) {
        WampBroker.removeTopic(wampApp, getFQtopicURI("app_event."+app.getAppId()));
        applications.remove(app.getAppId());
    }
    

    @WampRPC(name="new_app")
    public WampDict newApp(WampSocket socket, WampDict data) throws Exception
    {
        // TODO: check it doesn't exists

        boolean valid = false;
        Client client = clients.get(socket.getSessionId());
        if(socket.getState() != WampConnectionState.AUTHENTICATED) {
            System.err.println("The user hasn't logged in");
            throw new WampException(null, WGS_MODULE_NAME + ".unknown_user", null, null);
        }
        
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
            
            tx.commit();
            manager.close();
            
            Storage.removeEntity(app);
            unregisterApplication(app);
            event = broadcastAppInfo(socket, app, "app_deleted", true);
            return event;
        } else {
            System.err.println("AppId " + appId + " doesn't exist");
            throw new WampException(null, WGS_MODULE_NAME + ".appid_not_found", null, null);
        }
    }
    
    
    private WampDict broadcastAppInfo(WampSocket socket, Application app, String cmd, boolean excludeMe) throws Exception
    {
        WampDict event = app.toWampObject();
        event.put("cmd", cmd);
        socket.publishEvent(WampBroker.getTopic(getFQtopicURI("apps_event")), null, event, excludeMe, false);
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
                g = groups.get(gid);
                if(g == null) g = Storage.findEntity(Group.class, gid);
                
                if(g != null) {
                    logger.log(Level.INFO, "open_group: group found: " + gid);
                    valid = true;
                } 
                
        } else {
            
            if(options.has("automatch") && options.getBoolean("automatch")) {
                Application app = applications.get(appId);
                if(app == null) {
                    List<Application> list = Storage.findEntities(Application.class, "wgs.findAppByName", appId );
                    if(list.size() > 0) app = list.get(0);
                }
                
                if(app != null) {
                    autoMatchMode = true;
                    
                    String jpaQuery = "SELECT DISTINCT OBJECT(g) FROM AppGroup g WHERE g.state = org.wgs.service.game.GroupState.OPEN AND g.autoMatchEnabled = TRUE AND g.autoMatchCompleted = FALSE AND g.application = :application";
                    // TODO: automatch criteria (opponents, role, ELO range, game variant, time criteria,...)                    
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
                            
                            String role = "";
                            if(options.has("role")) role = options.getText("role");
                            if(role.length() > 0) {
                                for(Member m : g.getMembers()) {
                                    if(!m.getRole().isMultiple() && role.equals(m.getRole().getName()) && m.getUser() != null) {
                                        valid = false;
                                        g = null;
                                        break;
                                    }
                                }
                            }
                        }
                        
                        if(valid) {
                            break;
                        } else {
                            g = null;
                            manager.lock(tmp, LockModeType.NONE);  // FIXME: MySQL holds lock
                        }
                    } 
                    
                }                
                logger.log(Level.INFO, "open_group: search group for automatch");

            }
        } 
        
        if(g != null) {
            gid = g.getGid();
            groups.put(gid, g);            
            String pwd = g.getPassword();
            if( (pwd != null) && (pwd.length()>0) ) {
                String pwd2 = (options!=null && options.has("password"))? options.getText("password") : "";
                if(!pwd.equals(pwd2)) {
                    System.err.println("Incorrect password");
                    throw new WampException(null, WGS_MODULE_NAME + ".incorrectpassword", null, null);
                }
            }
            
        } else if(!spectator) {  
            // create group
            try {
                logger.log(Level.FINE, "open_group: creating new group");
                Application app = applications.get(appId);
                if(app == null) {
                    List<Application> list = Storage.findEntities(Application.class, "wgs.findAppByName", appId);
                    if(list.size() > 0) app = list.get(0);
                }                
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
                g.setAdmin(client.getUser());
                g.setAutoMatchEnabled(autoMatchMode);
                g.setAutoMatchCompleted(false);
                if(options != null) {
                    if(options.has("data")) {
                        g.setData(options.getText("data"));
                    }
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
                

                //updateAppInfo(socket, app, "app_updated", false);

                GroupActionValidator validator = null;
                String validatorClassName = g.getApplication().getActionValidator();
                if(validatorClassName != null) validator = (GroupActionValidator)Class.forName(validatorClassName).newInstance();
            
                if(validator == null || validator.validAction(this.applications.values(), g, "INIT", g.getData(), -1L)) {
                    app.addGroup(g);
                    groups.put(g.getGid(), g);
                    created = true;
                    valid = true;
                }

            } catch(Exception err) {
                // valid = false;
            }

        }

        // generate response:
        WampDict response = (g!=null)? g.toWampObject(true) : new WampDict();
        response.put("cmd", "user_joined");

        if(valid) synchronized(g) {
            Application app = g.getApplication();
            ArrayList<String> requiredRoles = new ArrayList<String>();
            for(Role r : app.getRoles()) {
                if(r.isRequired()) requiredRoles.add(r.getName());
            }
            
            response.put("created", created);
            response.put("app", app.toWampObject());

            String topicName = getFQtopicURI("group_event." + g.getGid());
            WampTopic topic = WampBroker.getTopic(topicName);
            if(topic == null) {
                WampTopicOptions topicOptions = new WampTopicOptions();
                topic = WampBroker.createTopic(wampApp, topicName, topicOptions);
            }
            
            //WampSubscriptionOptions subscriptionOptions = new WampSubscriptionOptions(null);
            //WampServices.subscribeClientWithTopic(wampApp, client.getSocket(), null, topicName, subscriptionOptions);
            
            client.addGroup(g);
            WampList conArray = new WampList();
            for(WampSubscription subscription : topic.getSubscriptions()) {
                for(Long sid : subscription.getSessionIds(socket.getRealm())) {
                    Client c = clients.get(sid);
                    User u = ((c!=null)? c.getUser() : null);
                    String user = ((u == null) ? "" : u.getUid());
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
                User currentUser = client.getUser();
                int avail_slots = 0;
                int minSlot = 0;
                int maxSlot = Math.max(num_slots, g.getMinMembers());
                if(options.has("slot")) {
                    minSlot = options.getLong("slot").intValue();
                    maxSlot = minSlot+1;
                }
                
                for(int index = minSlot; index < maxSlot; index++) {
                    Member member = null;
                    member = g.getMember(index);
                    boolean connected = (member != null) && (member.getClient() != null);
                    String user = ((member == null || member.getUser() == null) ? "" : member.getUser().getUid() );
                    if(!connected && (options.has("slot") || (currentUser!=null && user.equals(currentUser.getUid()))) ) {
                        reserved = true;
                        reservedSlot = index;
                        break;
                    } else if(member == null || member.getUser() == null) {
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

            WampList opponents = new WampList();
            if(options.has("opponents")) opponents = (WampList)options.get("opponents");
            if(options.has("role")) requiredRoles.remove(options.getText("role"));
            
            // int requiredSlot = (options != null && options.has("slot"))? options.getLong("slot").intValue() : -1;
            for(int index = 0;
                    ((index < Math.max(num_slots, g.getMinMembers())) || (requiredRoles.size() > 0));
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
                
                boolean userUpdated = false;
                boolean connected = (member.getClient() != null);
                if(!spectator && !joined && ( (reserved && index == reservedSlot) || (!reserved && member.getUser() == null) ) ) {
                    response.put("slotJoinedByClient", member.getSlot());
                    member.setClient(client);
                    member.setState(MemberState.JOINED);
                    member.setUser(manager.getReference(User.class, client.getUser().getUid()));
                    if(options != null && options.has("role") && options.getText("role").length() > 0) {
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
                    userUpdated = true;
                }
                
                User u = member.getUser();
                if(u != null) {
                    for(int i = opponents.size()-1; i >= 0; i--) {
                        WampDict opponent = (WampDict)opponents.get(i);
                        if(opponent.getText("user").equals(u.getUid())) opponents.remove(i);
                    }
                } else if(opponents.size() > 0) {
                    WampDict opponent = (WampDict)opponents.remove(0);
                    String user = opponent.getText("user");
                    u = Storage.findEntity(User.class, user);
                    member.setUser(u);
                    userUpdated = true;
                }           
                
                if(userUpdated) {
                    WampDict event = member.toWampObject();
                    event.put("cmd", "user_joined");
                    //event.put("sid", client.getSessionId());
                    //event.put("user", member.getUser().getFQid());
                    //event.put("name", member.getUser().getName());
                    //event.put("picture", member.getUser().getPicture());
                    event.put("gid", g.getGid());
                    event.put("valid", true);

                    socket.publishEvent(WampBroker.getTopic(getFQtopicURI("group_event."+g.getGid())), null, event, true, false);  // exclude Me
                }

            }

            response.put("members", getMembers(g, 0));
            
            broadcastAppEventInfo(socket, g, created? "group_created" : "group_updated");
            
            if(created) notifyOfflineUsers(socket, g, getActionNameDescription("INIT"));
            
            g.setVersion(Storage.saveEntity(g).getVersion());

        }

        
        if(valid && !created && !joined) {
            User u = client.getUser();
            Long sid = client.getSessionId();
            String user = ( (u == null) ? "" : u.getUid() );

            WampDict event = new WampDict();
            event.put("cmd", "user_joined");
            event.put("gid", g.getGid());
            event.put("user", user);
            event.put("name", ((u == null)? "" : u.getName()) );
            event.put("picture", ((u == null)? null : u.getPicture()) );
            event.put("sid", sid);
            event.put("type", "user");
            event.put("valid", valid);
                    
            socket.publishEvent(WampBroker.getTopic(getFQtopicURI("group_event."+g.getGid())), null, event, true, false);  // exclude Me
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
        boolean excludeMe = true;
        boolean broadcastAppInfo = false;
        boolean broadcastGroupInfo = false;
        String appId = node.getText("app");
        String gid = node.getText("gid");

        WampDict response = new WampDict();
        response.put("cmd", "group_updated");
        response.put("sid", socket.getSessionId());
        
        
        Group g = groups.get(gid);
        if(g != null) synchronized(g) {
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

            
            response.putAll(g.toWampObject(true));
            if(node.has("state")) {            
                if(g.getState() == GroupState.STARTED && node.has("ready") && node.getBoolean("ready") ) {
                    for(int slot = 0; slot < g.getNumSlots(); slot++) {
                        Member member = g.getMember(slot);
                        if(member != null && member.getClient() != null && socket.getSessionId().equals(member.getClient().getSessionId())) {
                            member.setState(MemberState.READY);
                            excludeMe = false;
                        }
                    }
                }
            }
            
            response.put("members", getMembers(g,0));            

            g.setVersion(Storage.saveEntity(g).getVersion());
            
            valid = true;
        }

        response.put("valid", valid);

        if(broadcastAppInfo)    broadcastAppEventInfo(socket, g, "group_updated");
        if(broadcastGroupInfo)  socket.publishEvent(WampBroker.getTopic(getFQtopicURI("group_event."+g.getGid())), null, response, excludeMe, false);  // exclude Me
        return response;
    }
    
    
    @WampRPC(name="list_members")
    public WampList getMembers(String gid, int team) throws Exception 
    {
        Group g = groups.get(gid);
        if(g == null) {
            g = Storage.findEntity(Group.class, gid);
            if(g != null) groups.put(gid, g);
            else throw new WampException(null, "wgs.error.group_not_found", null, null);
        }
        
        return getMembers(g, team);
    }
    
    private WampList getMembers(Group g, int team) 
    {
        WampList membersArray = new WampList();

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
            if(g != null) synchronized(g) {
                logger.log(Level.FINE, "open_group: group found: " + gid);
                
                response.putAll(g.toWampObject(true));
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

                        Long sid = data.getLong("sid");
                        Client c = (sid != null) ? clients.get(sid) : null;
                        if(c!=null) {
                            // when it's not a reservation of a member slot
                            User u = c.getUser();
                            if(u!=null) userId = u.getUid();
                        }

                        Role r = g.getApplication().getRoleByName(role);

                        // TODO: check "slot" is valid
                        User user = Storage.findEntity(User.class, userId);

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
                
                if(valid) g.setVersion(Storage.saveEntity(g).getVersion());
            }

            response.put("valid", valid);

            if(valid) {
                //response.putAll(g.toJSON());
                broadcastAppEventInfo(socket, g, "group_updated");
                socket.publishEvent(WampBroker.getTopic(getFQtopicURI("group_event."+g.getGid())), null, response, false, false);
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
            socket.publishEvent(WampBroker.getTopic(getFQtopicURI("group_event."+gid)), null, event, false, true); // don't exclude Me
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
                    socket.publishEvent(WampBroker.getTopic(getFQtopicURI("group_event."+g.getGid())), null, event, false, true);
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

            if(g != null) synchronized(g) {
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
                            member.setState(MemberState.DETACHED);
                            g.setMember(slot, member);
                            
                            WampDict obj = member.toWampObject();
                            membersArray.add(obj);
                            
                            g.setVersion(Storage.saveEntity(g).getVersion());

                        } else {
                            num_members++;
                        }
                    }
                }
                response.put("members", membersArray);

                socket.publishEvent(WampBroker.getTopic(getFQtopicURI("group_event."+gid)), null, response, true, false); // exclude Me

                client.removeGroup(g);
                
                String topicName = getFQtopicURI("group_event." + g.getGid());

                WampTopic topic = WampBroker.getTopic(topicName);
                for(WampSubscription subscription : topic.getSubscriptions()) {
                    subscription.removeSocket(socket.getSessionId());
                }
                
                broadcastAppEventInfo(socket, g, "group_updated");                
                
            }

            return response;
    }
    
    
    @WampRPC(name="delete_finished_groups")
    public void deleteFinishedGroups(WampSocket socket) throws Exception
    {   
        EntityManager manager = Storage.getEntityManager();
        TypedQuery<Group> query = manager.createNamedQuery("wgs.findFinishedGroupsFromUser", Group.class);
        query.setParameter(1, socket.getUserPrincipal());
        
        for(Group g : query.getResultList()) {
            int refCount = 0;
            for(Member m : g.getMembers()) {
                if(m.getState() == MemberState.EMPTY || m.getUser().equals(socket.getUserPrincipal())) {
                    m.setState(MemberState.DELETED);
                    Storage.saveEntity(m);
                } 
                if(m.getState() != MemberState.DELETED) {
                    refCount++;
                }
            }
            if(refCount == 0) Storage.removeEntity(g);
        }
        
        manager.close();
    }

    
    private void notifyOfflineUsers(WampSocket socket, Group g, String msg) throws Exception    
    {
        System.out.println("Starting OFFLINE notifications");
        if(g != null && msg != null) {
            for(Member m : g.getMembers()) {
                if(m != null && m.getUser() != null) {
                    Client c = m.getClient();
                    if(c == null) {
                        System.out.println("OFFLINE notifications to " + m.getUser().getName());
                        Social.notifyUser(socket, m.getUser(), g.getGid(), msg);
                    }
                }
            }
        }
        System.out.println("Finished OFFLINE notifications");
    }
    
    
    private String getActionNameDescription(String actionName)
    {
        switch(actionName) {
            case "INIT":
                return "%me% started a game with you, play now!";
            case "MOVE":
                return "%me% has moved, and now it's your turn!";
            case "RESIGN":
                return "%me% has resigned the game!";
            case "DRAW_QUESTION":
                return "%me% offers a draw!";
            case "DRAW_ACCEPTED":
                return "%me% accepted the draw offer!";                
            case "DRAW_REJECTED":
                return "%me% rejected the draw offer!";                
            case "RETRACT_QUESTION":
                return "%me% wants to retract last move!";
            case "RETRACT_ACCEPTED":
                return "%me% accepted to retract last move!";
            case "RETRACT_REJECTED":
                return "%me% rejected to retract last move!";
            default:
                return actionName;
        }
    }

    
    private void broadcastAppEventInfo(WampSocket socket, Group g, String cmd) throws Exception
    {
        WampDict event = g.toWampObject(false);
        event.put("cmd", cmd);
        event.put("members", getMembers(g,0));

        HashSet<Long> eligible = new HashSet<Long>();
        eligible.add(socket.getSessionId());
        for(Member m : g.getMembers()) {
            if(m != null && m.getUser() != null) {
                Set<Long> sessions = wampApp.getSessionsByUser(m.getUser());
                if(sessions != null) eligible.addAll(sessions);
            }
        }
        
        WampBroker.publishEvent(socket.getRealm(), WampProtocol.newGlobalScopeId(), WampBroker.getTopic(getFQtopicURI("apps_event")), null, event, eligible, null, null, null, null, null);
        
        socket.publishEvent(WampBroker.getTopic(getFQtopicURI("app_event." + g.getApplication().getAppId())), null, event, false, false);     // broadcasts to all application subscribers
    }
    
    private void broadcastGroupEventInfo(WampSocket socket, Group g, String cmd, boolean excludeMe) throws Exception
    {
        WampDict event = g.toWampObject(true);
        event.put("cmd", cmd);
        event.put("members", getMembers(g,0));
        socket.publishEvent(WampBroker.getTopic(getFQtopicURI("group_event."+g.getGid())), null, event, excludeMe, false);
    }    
    
    
    @WampRPC(name = "list_groups")
    public WampDict listGroups(WampSocket socket, String appId, GroupState state, GroupFilter.Scope scope) throws Exception
    {
        Client client = clients.get(socket.getSessionId());
        GroupFilter filter = new GroupFilter(appId, state, scope, client.getUser());
                
        WampList groupsArray = new WampList();
        for(Group t : filter.getGroups()) {
            Group g = groups.get(t.getGid());
            if(g == null) g = t;
            
            if(!g.isHidden()) {
                WampDict obj = g.toWampObject(false);
                obj.put("members", getMembers(g,0));                
                groupsArray.add(obj);
            }
        }   
        
        WampDict retval = new WampDict();
        retval.put("groups", groupsArray);
        
        if(appId != null) {
            Application app = applications.get(appId);
            if(app != null) retval.put("app", app.toWampObject());
        }
        
        return retval;
    }    

    
    @WampRPC(name = "add_action")
    public boolean addAction(WampSocket socket, String gid, Long playerSlot, String actionName, String actionValue) throws Exception
    {
        Group g = groups.get(gid);
        if(g != null) synchronized(g) {
            String state = g.getData();

            GroupActionValidator validator = null;
            String validatorClassName = g.getApplication().getActionValidator();
            if(validatorClassName != null) validator = (GroupActionValidator)Class.forName(validatorClassName).newInstance();

            Member member = null;
            if(playerSlot >= 0) {
                member = g.getMember(playerSlot.intValue());
                if(!member.getUser().equals(socket.getUserPrincipal())) throw new WampException(null, "wgs.incorrect_user_member", null, null);
            }
            
            if(validator == null || validator.validAction(this.applications.values(), g, actionName, actionValue, playerSlot)) {
                GroupAction action = new GroupAction();
                
                action.setApplicationGroup(g);
                action.setActionOrder(g.getActions().size()+1);
                action.setActionName(actionName);
                action.setActionValue(actionValue);
                action.setActionTime(Calendar.getInstance());
                action.setSlot(-1);
                action.setUser((User)socket.getUserPrincipal());
                if(member != null) {
                    action.setSlot(member.getSlot());
                }
                
                g.getActions().add(action);
                g.setVersion(Storage.saveEntity(g).getVersion());

                boolean excludeMe = false;
                WampDict event = new WampDict();
                event.put("gid", g.getGid());
                event.put("action", action.toWampObject());
                
                socket.publishEvent(WampBroker.getTopic(getFQtopicURI("group_event."+g.getGid())), null, event, excludeMe, false);
                broadcastAppEventInfo(socket, g, "group_updated"); // i.e: turn change
                notifyOfflineUsers(socket, g, getActionNameDescription(actionName));
                
                return true;
                
            }
        }
        return false;
    }
    
}
