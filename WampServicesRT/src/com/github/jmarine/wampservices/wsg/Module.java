/**
 * WebSocket Game services implementation
 *
 * @author Jordi Marine Fort 
 */

package com.github.jmarine.wampservices.wsg;


import com.github.jmarine.wampservices.WampApplication;
import com.github.jmarine.wampservices.WampException;
import com.github.jmarine.wampservices.WampModule;
import com.github.jmarine.wampservices.WampRPC;
import com.github.jmarine.wampservices.WampSocket;
import com.github.jmarine.wampservices.WampSubscriptionOptions;
import com.github.jmarine.wampservices.WampTopic;
import com.github.jmarine.wampservices.WampTopicOptions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;


public class Module extends WampModule 
{
    private static final Logger logger = Logger.getLogger(Module.class.toString());
    private static final String MODULE_URL = WampApplication.WAMP_BASE_URL + "/wsgservice#";
    private static final String PU_NAME = "WsgPU";
    private static final String LOCAL_USER_DOMAIN = "";
    
    private WampApplication wampApp = null;
    private EntityManagerFactory emf = Persistence.createEntityManagerFactory(PU_NAME);        
    private Map<String,Client> clients = new ConcurrentHashMap<String,Client>();
    private Map<String, Application> applications = new ConcurrentHashMap<String,Application>();
    private Map<String, Group> groups = new ConcurrentHashMap<String,Group>();
    
    public Module(WampApplication app)
    {
        super(app);
        this.wampApp = app;
        
        wampApp.createTopic(getFQtopicURI("apps_event"), null);

        try {
            List<Application> apps = findEntities(Application.class, "wsg.findAllApps");
            for(Application a : apps) {
                System.out.println("Application found in DB: " + a.getName());
                registerApplication(a);
            }
        } catch(Exception ex) {
            System.out.println("Error loading WSG applications: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
    
    
    public synchronized EntityManager getEntityManager()
    {
        EntityManager manager = emf.createEntityManager();
        return manager;
    }
    
    
    public void saveEntity(Object entity)
    {
        EntityManager manager = getEntityManager();
        EntityTransaction transaction = manager.getTransaction();
        transaction.begin();
        
        manager.merge(entity);

        transaction.commit();
        manager.close();        
    }
    
    public void removeEntity(Object entity)
    {
        EntityManager manager = getEntityManager();
        EntityTransaction transaction = manager.getTransaction();
        transaction.begin();
        
        entity = manager.merge(entity);
        manager.remove(entity);

        transaction.commit();
        manager.close();        
    }    
    
    public <T> List<T> findEntities(Class<T> cls, String namedQueryName, Object[] ... params)
    {
        EntityManager manager = getEntityManager();

        javax.persistence.Query query = manager.createNamedQuery(namedQueryName, cls);
        if(params != null) {
            for(int index = 0; index < params.length; index++) {
                query.setParameter(index, params[index]);
            }
        }

        List<T> entities = query.getResultList();

        manager.close();

        return entities;
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
    public Object onCall(WampSocket socket, String method, ArrayNode args) throws Exception 
    {
        Object retval = null;
        if(method.equals("list_groups")) {
            String appId = args.get(0).asText();
            wampApp.subscribeClientWithTopic(socket, getFQtopicURI("app_event:"+appId), null);
            retval = listGroups(appId);
        } else {
            retval = super.onCall(socket, method, args);
        }
        return retval;
    }

    @Override
    public void onConnect(WampSocket socket) throws Exception {
        super.onConnect(socket);
        Client cli = new Client();
        cli.setSocket(socket);
        cli.setState(ClientState.INVALID);  // not authenticated
        clients.put(socket.getSessionId(), cli);
        
        wampApp.subscribeClientWithTopic(socket, getFQtopicURI("apps_event"), null);
    }
    
    @Override
    public void onDisconnect(WampSocket socket) throws Exception {
        wampApp.unsubscribeClientFromTopic(socket, getFQtopicURI("apps_event"));
        Client client = clients.get(socket.getSessionId());
        for(String gid : client.getGroups().keySet()) {
            exitGroup(socket, gid);
        }
        clients.remove(socket.getSessionId());
        super.onDisconnect(socket);
    }
    
    
    @WampRPC(name="register")
    public ArrayNode registerUser(WampSocket socket, ObjectNode data) throws Exception
    {
        boolean user_valid = false;
        User usr = null;
        
        Client client = clients.get(socket.getSessionId());

        String uid = data.get("uid").asText();
        UserId userId = new UserId(uid, LOCAL_USER_DOMAIN);
        
        EntityManager em = getEntityManager();
        usr = getEntityManager().find(User.class, userId);
        em.close();
        
        if(usr != null) throw new WampException(MODULE_URL + "uidexists", "The uid is reserved by another user");
        
        usr = new User();
        usr.setProfileCaducity(null);
        usr.setUid(uid);
        usr.setOpenIdProviderUrl(LOCAL_USER_DOMAIN);
        if(uid.length() == 0) usr.setName("");
        else usr.setName(Character.toUpperCase(uid.charAt(0)) + uid.substring(1));
        usr.setPassword(data.get("password").asText());
        usr.setEmail(data.get("email").asText());
        usr.setAdministrator(false);
        saveEntity(usr);

        client.setUser(usr);
        client.setState(ClientState.AUTHENTICATED);

        return null;
    }
    
    
    @WampRPC
    public ArrayNode login(WampSocket socket, ObjectNode data) throws Exception
    {
        boolean user_valid = false;
        Client client = clients.get(socket.getSessionId());

        String uid = data.get("uid").asText();
        String password  = data.get("password").asText();

        EntityManager manager = getEntityManager();
        UserId userId = new UserId(uid, LOCAL_USER_DOMAIN);
        User usr = manager.find(User.class, userId);
        if( (usr != null) && (password.equals(usr.getPassword())) ) {
            user_valid = true;
            client.setUser(usr);
            client.setState(ClientState.AUTHENTICATED);
        }

        if(!user_valid) throw new WampException(MODULE_URL + "loginerror", "Login credentials are not valid");
        return null;
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
        if(client.getState() == ClientState.INVALID) throw new WampException(MODULE_URL + "unknownuser", "The user hasn't logged in");
        
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

        saveEntity(app);
        registerApplication(app);
        valid = true;

        // TODO:  broadcast new application to subscribed clients
        ObjectNode event = updateAppInfo(socket, app, "app_created", true);
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
            removeEntity(app);
            unregisterApplication(app);
            event = updateAppInfo(socket, app, "app_deleted", true);
            return event;
        } else {
            throw new WampException(Module.MODULE_URL + "#appidnotfound", "AppId " + appId + " doesn't exist");
        }
    }
    
    
    private ObjectNode updateAppInfo(WampSocket socket, Application app, String cmd, boolean excludeMe) throws Exception
    {
        // check subscribers of "apps_event" topic
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode event = app.toJSON();
        event.put("cmd", cmd);
        socket.publishEvent(wampApp.getTopic(getFQtopicURI("apps_event")), event, excludeMe);
        return event;
    }

    private ObjectNode updateGroupInfo(WampSocket socket, Group g, String cmd, boolean excludeMe) throws Exception
    {
        // check subscribers of "apps_event" topic
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode event = g.toJSON();
        event.put("cmd", cmd);
        if(!g.isHidden()) {
            socket.publishEvent(wampApp.getTopic(getFQtopicURI("app_event:"+g.getApplication().getAppId())), event, excludeMe);
        }
        return event;
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
                groupsArray.add(obj);
            }   

            retval.put("groups", groupsArray);
            
        }

        return retval;
        
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
                String pwd2 = (options!=null)? options.get("password").asText() : "";
                if(!pwd.equals(pwd2)) throw new WampException(MODULE_URL + "incorrectpassword", "Incorrect password");
            }
            
        } else if(!spectator) {  
            // create group
            try {
                logger.log(Level.FINE, "open_group: creating new group");
                Application app = applications.get(appId);
                g = new Group();
                g.setGid(UUID.randomUUID().toString());
                g.setDescription(client.getUser().getUid() + ": " + g.getGid());
                g.setApplication(app);
                g.setState(GroupState.OPEN);
                g.setObservableGroup(app.isObservableGroup());
                g.setDynamicGroup(app.isDynamicGroup());
                g.setAlliancesAllowed(app.isAlliancesAllowed());
                g.setMaxMembers(app.getMaxMembers());
                g.setMinMembers(app.getMinMembers());
                g.setDeltaMembers(app.getDeltaMembers());
                g.setAdminUid(client.getUser().getUid());
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
                    if(!autoMatchMode && options.has("password")) {
                        String password = options.get("password").asText();
                        g.setPassword( (password!=null && password.length()>0)? password : null);
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
            
            response.put("created", created);
            response.put("app", app.toJSON());

            ArrayNode rolesArray = mapper.createArrayNode();
            for(Role r : app.getRoles()) {
                rolesArray.add(r.toJSON());
                if(r.isRequired()) requiredRoles.add(r.getName());
            }
            response.put("roles", rolesArray);


            String topicName = getFQtopicURI("group_event:" + g.getGid());
            WampTopic topic = wampApp.getTopic(topicName);
            if(topic == null) {
                WampTopicOptions topicOptions = new WampTopicOptions();
                topicOptions.setPublisherIdRevelationEnabled(true);
                topic = wampApp.createTopic(topicName, topicOptions);
            }
            WampSubscriptionOptions subscriptionOptions = new WampSubscriptionOptions();
            subscriptionOptions.setPublisherIdRequested(true);
            wampApp.subscribeClientWithTopic(client.getSocket(), topicName, subscriptionOptions);
            
            client.addGroup(g);
            ArrayNode conArray = mapper.createArrayNode();
            for(String sid : topic.getSessionIds()) {
                    Client c = clients.get(sid);
                    User u = ((c!=null)? c.getUser() : null);
                    String uid = ((u == null) ? "" : u.getUid());

                    ObjectNode con = mapper.createObjectNode();
                    con.put("uid", uid);
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
                    String uidName = ((member == null || member.getUid() == null) ? "" : member.getUid() );
                    if(!connected && uidName == currentUser.getUid()) {
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

            ArrayNode membersArray = mapper.createArrayNode();
            for(int index = 0;
                    (index < Math.max(num_slots, g.getMinMembers())) || (requiredRoles.size() > 0);
                    index++) {
                String usertype = "user";
                int team = 1+index;

                Member member = g.getMember(index);
                if(member == null) member = new Member();
                
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
                    member.setState(MemberState.RESERVED);
                    member.setUid(client.getUser().getUid());
                    member.setUserType("user");
                    member.setTeam(1+index);
                    g.setMember(index, member);
                    client.setState(ClientState.JOINED);
                    joined = true;
                    connected = true;

                    ObjectNode event = member.toJSON();
                    event.put("cmd", "user_joined");
                    event.put("gid", g.getGid());
                    event.put("slot", index);
                    event.put("valid", true);

                    socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+g.getGid())), event, true);  // exclude Me
                }


                ObjectNode memberObj = member.toJSON();
                memberObj.put("slot", index);
                memberObj.put("connected", connected);
                
                membersArray.add(memberObj);
            }

            response.put("members", membersArray);
            
            updateGroupInfo(socket, g, created? "group_created" : "group_updated", false);
        }

        
        if(valid && !created && !joined) {
            User u = client.getUser();
            String sid = client.getSessionId();
            String uidName = ( (u == null) ? "" : u.getUid() );

            ObjectNode event = mapper.createObjectNode();
            event.put("cmd", "user_joined");
            event.put("gid", g.getGid());
            event.put("uid", uidName);
            event.put("sid", sid);
            event.put("type", "user");
            event.put("valid", valid);
                    
            socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+g.getGid())), event, true);  // exclude Me
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
        
        
        Group g = groups.get(gid);
        if(g != null) {
            response = g.toJSON();
            response.put("cmd", "group_updated");
            
            logger.log(Level.FINE, "open_group: group found: " + gid);
            
            response.put("gid", g.getGid());
            
            if(node.has("automatch")) {
                boolean autoMatchMode = node.get("automatch").asBoolean();
                g.setAutoMatchEnabled(autoMatchMode);
                g.getApplication().addAutoMatchGroup(g);
            } 

            if(node.has("dynamic")) {
                boolean dynamic = node.get("dynamic").asBoolean();
                g.setDynamicGroup(dynamic);
                response.put("dynamic", g.isDynamicGroup());
            }

            if(node.has("hidden")) {
                boolean hidden = node.get("hidden").asBoolean();
                g.setHidden(hidden);
                response.put("hidden", g.isHidden());
                broadcastAppInfo = true;
            }            
            
            if(node.has("observable")) {
                boolean observable = node.get("observable").asBoolean();
                g.setObservableGroup(observable);
                response.put("observable", g.isObservableGroup());
                broadcastAppInfo = true;
            }                                 
            
            if(node.has("data")) {
                String data = node.get("data").asText();
                g.setData(data);
                response.put("data", data);
                broadcastGroupInfo = true;
            }
            
            if(node.has("state")) {
                String state = node.get("state").asText();
                g.setState(GroupState.valueOf(state));
                response.put("state", state);
                
                if(g.getState() == GroupState.STARTED) {
                    for(int slot = 0; slot < g.getNumSlots(); slot++) {
                        Member member = g.getMember(slot);
                        if(member != null && member.getClient() != null && socket.getSessionId().equals(member.getClient().getSessionId())) {
                            member.setState(MemberState.READY);
                        }
                    }
                    response.put("updates", getMembers(gid,0));
                }
                
                //updateAppInfo(socket, g.getApplication(), "app_updated", false);
                broadcastAppInfo = true;
                broadcastGroupInfo = true;
            }
            
            valid = true;
        }

        response.put("valid", valid);

        if(broadcastAppInfo)  socket.publishEvent(wampApp.getTopic(getFQtopicURI("app_event:"+g.getApplication().getAppId())), response, true);  // exclude Me
        if(broadcastGroupInfo) socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+g.getGid())), response, true);  // exclude Me
        return response;
    }
    
    
    @WampRPC(name="list_members")
    public ArrayNode getMembers(String gid, int team) throws Exception 
    {
        Group g = groups.get(gid);
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode membersArray = mapper.createArrayNode();
             
        for(int slot = 0, numSlots = g.getNumSlots(); slot < numSlots; slot++) {
            Member member = g.getMember(slot);
            if( (member != null) && (team==0 || team==member.getTeam()) ) {
                boolean connected = (member.getClient() != null);

                ObjectNode obj = member.toJSON();
                obj.put("slot", slot);
                obj.put("connected", connected);
                
                membersArray.add(obj);
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
            response.put("cmd", "user_updated");

            Group g = groups.get(gid);
            if(g != null) {
                logger.log(Level.FINE, "open_group: group found: " + gid);
                
                response.put("gid", g.getGid());
                if(data.has("slot")) {
                    
                    // UPDATE MEMBER SLOT
                    String sid = data.get("sid").asText();
                    
                    int slot = data.get("slot").asInt();
                    String uid = data.get("uid").asText();
                    String role = data.get("role").asText();
                    String usertype = data.get("type").asText();
                    int team = data.get("team").asInt();

                    Client c = clients.get(sid);
                    if(c!=null) {
                        // when it's not a reservation of a member slot
                        User u = c.getUser();
                        if(u!=null) uid = u.getUid();
                    }

                    Role r = g.getApplication().getRoleByName(role);

                    // TODO: check "slot" is valid
                    Member member = g.getMember(slot);
                    if(member == null) member = new Member();
                    if(c==null) member.setState(MemberState.EMPTY);
                    else if(c != member.getClient()) member.setState(MemberState.RESERVED);
                    member.setClient(c);
                    member.setUid(uid);
                    member.setUserType(usertype);
                    member.setRole(r);
                    member.setTeam(team);
                    g.setMember(slot, member);


                    response.put("sid", sid);
                    response.put("uid", uid);
                    response.put("type", usertype);
                    response.put("slot", slot);
                    response.put("role", role);
                    response.put("team", team);
                    response.put("state", String.valueOf(member.getState()));
                    valid = true;
                    
                } else {
                    // UPDATE CLIENT STATE ("reserved" <--> "ready")
                    String sid = socket.getSessionId();
                    ArrayNode membersArray = mapper.createArrayNode();
                    JsonNode stateNode = data.get("state");
                    String state = (stateNode!=null && !stateNode.isNull()) ? stateNode.asText() : null;
                    if(state != null) {
                        for(int slot = 0, numSlots = g.getNumSlots(); slot < numSlots; slot++) {
                            Member member = g.getMember(slot);
                            if( (member != null) && (member.getClient() != null) && (member.getClient().getSessionId().equals(sid)) ) {
                                boolean connected = (member.getClient() != null);
                                member.setState(MemberState.valueOf(state));
                                ObjectNode obj = member.toJSON();
                                obj.put("slot", slot);
                                obj.put("connected", connected);
                                membersArray.add(obj);
                            }
                        }
                        response.put("updates", membersArray);
                    }
                    valid = true;
                }
            }

            response.put("valid", valid);

            if(valid) socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+g.getGid())), response, true);  // exclude Me
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
            socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+gid)), event, false); // don't exclude Me
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
                    wampApp.publishEvent(socket.getSessionId(), wampApp.getTopic(getFQtopicURI("group_event:"+g.getGid())), event, null, eligibleSet);
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
            response.put("cmd", "user_unjoined");

            Group g = groups.get(gid);

            if(g != null) {
                String appId = g.getApplication().getAppId();
                logger.log(Level.FINE, "open_group: group found: " + gid);

                response.put("gid", g.getGid());
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
                            member.setState(MemberState.EMPTY);
                            member.setUid(null);
                            member.setUserType("user");
                            g.setMember(slot, member);
                            
                            if(g.isAutoMatchEnabled() && g.isAutoMatchCompleted()) {
                                g.setAutoMatchCompleted(false);
                                g.getApplication().addAutoMatchGroup(g);
                            }                            
                            
                            ObjectNode obj = member.toJSON();
                            obj.put("slot", slot);
                            membersArray.add(obj);

                        } else {
                            num_members++;
                        }
                    }
                }
                response.put("members", membersArray);

                response.put("valid", true);
                socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+gid)), response, true); // exclude Me

                client.removeGroup(g);
                
                String topicName = getFQtopicURI("group_event:" + g.getGid());
                for(WampTopic topic : wampApp.unsubscribeClientFromTopic(socket, topicName)) {
                    boolean deleted = false;
                    if(topic.getSubscriptionCount() == 0) {
                        logger.log(Level.INFO, "closing group {0}: {1}", new Object[]{ g.getGid(), g.getDescription()});

                        groups.remove(g.getGid());
                        applications.get(appId).removeGroup(g);
                        
                        //updateAppInfo(socket, applications.get(appId), "app_updated", false);

                        wampApp.removeTopic(topicName);
                        deleted = true;
                    }
                    updateGroupInfo(socket, g, deleted? "group_deleted" : "group_updated", false);                    
                }
            }


            return response;
    }


}
