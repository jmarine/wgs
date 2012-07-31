package com.github.jmarine.wampservices.wsg;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import com.github.jmarine.wampservices.WampApplication;
import com.github.jmarine.wampservices.WampException;
import com.github.jmarine.wampservices.WampModule;
import com.github.jmarine.wampservices.WampSocket;
import com.github.jmarine.wampservices.WampTopic;
import org.json.JSONArray;
import org.json.JSONObject;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author jordi
 */
public class Module extends WampModule 
{
    private static final Logger logger = Logger.getLogger(Module.class.toString());
    private static final String MODULE_URL = WampApplication.WAMP_BASE_URL + "/wsgservice#";
    private static final String PU_NAME = "WsgPU";
    
    private WampApplication wampApp = null;
    private EntityManagerFactory emf = Persistence.createEntityManagerFactory(PU_NAME);        
    private Map<String,Client> clients = new ConcurrentHashMap<String,Client>();
    private Map<String, Application> applications = new ConcurrentHashMap<String,Application>();
    private Map<String, Group> groups = new ConcurrentHashMap<String,Group>();
    
    public Module(WampApplication app) {
        super(app);
        this.wampApp = app;
        
        wampApp.createTopic(getFQtopicURI("apps_event"));

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
    public Object onCall(WampSocket socket, String method, JSONArray args) throws Exception 
    {
        Object retval = null;
        if(method.equals("login")) {
            retval = login(socket, args.getJSONObject(0));
        } else if(method.equals("register")) {
            retval = registerUser(socket, args.getJSONObject(0));
        } else if(method.equals("list_apps")) {
            retval = listApps();
        } else if(method.equals("list_groups")) {
            String appId = args.getJSONObject(0).getString("app");
            wampApp.subscribeClientWithTopic(socket, getFQtopicURI("app_event:"+appId));
            retval = listGroups(appId);
        } else if(method.equals("new_app")) {
            retval = newApp(socket, args.getJSONObject(0));
        } else if(method.equals("delete_app")) {
            retval = deleteApp(socket, args.getJSONObject(0));
        } else if(method.equals("open_group")) {
            retval = openGroup(socket, args.getJSONObject(0));
        } else if(method.equals("exit_group")) {
            String gid = args.getJSONObject(0).getString("gid");
            retval = exitGroup(socket, gid);
        } else if(method.equals("update_group")) {
            retval = updateGroup(socket, args.getJSONObject(0));
        } else if(method.equals("update_member")) {
            retval = updateMember(socket, args.getJSONObject(0));
        } else {
            throw new WampException(WampException.WAMP_GENERIC_ERROR_URI, "Method not implemented: " + method);
        }
        return retval;
    }

    @Override
    public void onConnect(WampSocket socket) throws Exception {
        Client cli = new Client();
        cli.setSocket(socket);
        cli.setState(ClientState.INVALID);  // not authenticated
        clients.put(socket.getSessionId(), cli);
        
        wampApp.subscribeClientWithTopic(socket, getFQtopicURI("apps_event"));
    }
    
    @Override
    public void onDisconnect(WampSocket socket) throws Exception {
        wampApp.unsubscribeClientFromTopic(socket, getFQtopicURI("apps_event"));
        Client client = clients.get(socket.getSessionId());
        for(String gid : client.getGroups().keySet()) {
            exitGroup(socket, gid);
        }
        clients.remove(socket.getSessionId());
    }
    
    
    public JSONArray registerUser(WampSocket socket, JSONObject data) throws Exception
    {
        boolean user_valid = false;
        User usr = null;
        
        Client client = clients.get(socket.getSessionId());

        String nick = data.getString("nick");
        
        EntityManager em = getEntityManager();
        usr = getEntityManager().find(User.class, nick);
        em.close();
        
        if(usr != null) throw new WampException(MODULE_URL + "nickexists", "The nick is reserved by another user");
        
        usr = new User();
        usr.setNick(nick);
        usr.setPassword(data.getString("password"));
        usr.setEmail(data.getString("email"));
        usr.setAdministrator(false);
        saveEntity(usr);

        client.setUser(usr);
        client.setState(ClientState.AUTHENTICATED);

        return null;
    }
    
    
    public JSONArray login(WampSocket socket, JSONObject data) throws Exception
    {
        boolean user_valid = false;
        Client client = clients.get(socket.getSessionId());

        String nick = data.getString("nick");
        String password  = data.getString("password");

        EntityManager manager = getEntityManager();
        User usr = manager.find(User.class, nick);
        if( (usr != null) && (password.equals(usr.getPassword())) ) {
            user_valid = true;
            client.setUser(usr);
            client.setState(ClientState.AUTHENTICATED);
        }

        if(!user_valid) throw new WampException(MODULE_URL + "loginerror", "The credentials are not valid");
        return null;
    }
    
    
    
    private JSONObject listApps() throws Exception
    {
        // TODO: Filter by domain
        JSONObject retval = new JSONObject();
        JSONArray appArray = new JSONArray();
        for(Application app : applications.values()) {
            appArray.put(app.toJSON());
        }
        retval.put("apps", appArray);

        return retval;
    }
    
    
    private void registerApplication(Application app) {
        wampApp.createTopic(getFQtopicURI("app_event:"+app.getAppId()));
        applications.put(app.getAppId(), app);
    }
    
    private void unregisterApplication(Application app) {
        wampApp.deleteTopic(getFQtopicURI("app_event:"+app.getAppId()));
        applications.remove(app.getAppId());
    }
    

    private JSONObject newApp(WampSocket socket, JSONObject data) throws Exception
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
        app.setName(data.getString("name"));
        app.setDomain(data.getString("domain"));
        app.setVersion(data.getInt("version"));
        app.setMaxGroupMembers(data.getInt("max"));
        app.setMinGroupMembers(data.getInt("min"));
        app.setAlliancesAllowed(data.getBoolean("alliances"));
        app.setDynamicGroup(data.getBoolean("dynamic"));
        app.setObservableGroup(data.getBoolean("observable"));
        app.setAIavailable(data.getBoolean("ai_available"));        

        JSONArray roles = data.getJSONArray("roles");
        for(int i = 0; i < roles.length(); i++) {
            String roleName = roles.getString(i);
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
        broadcastApps(socket);

        
        JSONObject retval = new JSONObject();
        retval.put("cmd", "new_app");
        if(valid) {
            retval.put("app", app.toJSON());
        }

        if(valid) socket.publishEvent(wampApp.getTopic(getFQtopicURI("apps_event")), retval, true); // exclude Me
        return retval;
    }
        
    
    private JSONObject deleteApp(WampSocket socket, JSONObject param) throws Exception
    {
        // TODO: check user is administrator of app
        // TODO: delete groups
        // TODO: store in database

        boolean valid = false;
        String appId = param.getString("app");

        Application app = applications.get(appId);
        if(app != null) {
            removeEntity(app);
            unregisterApplication(app);
            broadcastApps(socket);
            valid = true;
        }


        JSONObject retval = new JSONObject();
        retval.put("cmd", "delete_app");
        if(valid) {
            retval.put("appId", appId);
        }
        
        if(valid) socket.publishEvent(wampApp.getTopic(getFQtopicURI("apps_event")), retval, true);  // exclude Me
        return retval;
    }
    
    
    private void broadcastApps(WampSocket socket) throws Exception
    {
        // check subscribers of "apps_event" topic
        JSONObject list = listApps();
        list.put("cmd", "list_apps");
        socket.publishEvent(wampApp.getTopic(getFQtopicURI("apps_event")), list, false);  // don't exclude Me
    }

    
    private JSONObject listGroups(String appId) throws Exception
    {
        System.out.println("Listing groups for app: '" + appId + "'");

        JSONObject retval = new JSONObject();
        JSONArray groupsArray = new JSONArray();
        Application app = applications.get(appId);
        if(app != null) {
            retval.put("app", app.toJSON());
            for(Group group : app.getGroupsByState(GroupState.OPEN)) {
                JSONObject obj = new JSONObject();
                obj.put("gid", group.getGid());
                obj.put("admin", group.getAdminNick());
                obj.put("num", group.getNumMembers());
                obj.put("min", group.getMinMembers());
                obj.put("max", group.getMaxMembers());
                obj.put("avail", group.getAvailSlots());
                obj.put("observable", group.isObservableGroup());
                obj.put("dynamic", group.isDynamicGroup());
                obj.put("alliances", group.isAlliancesAllowed());
                obj.put("description", group.getDescription());
                groupsArray.put(obj);
            }   

            retval.put("groups", groupsArray);
            
        }

        return retval;
        
    }
    
    
    private JSONObject openGroup(WampSocket socket, JSONObject data) throws Exception
    {
        Group   g = null;
        boolean valid   = false;
        boolean created = false;
        boolean joined  = false;


        Client client = clients.get(socket.getSessionId());
        // get group/app information
        try {
            String gid = data.getString("gid");
            g = groups.get(gid);
            if(g != null) {
                logger.log(Level.INFO, "open_group: group found: " + gid);
                valid = true;
            }

        } catch(Exception ex) {

            try {
                String appId = data.getString("app");
                Application app = applications.get(appId);
                if(app != null) {
                    g = new Group();
                    g.setGid(UUID.randomUUID().toString());
                    g.setDescription(client.getUser().getNick() + ": " + g.getGid());
                    g.setApplication(app);
                    g.setState(GroupState.OPEN);
                    g.setObservableGroup(app.isObservableGroup());
                    g.setDynamicGroup(app.isDynamicGroup());
                    g.setAlliancesAllowed(app.isAlliancesAllowed());
                    g.setMaxMembers(app.getMaxGroupMembers());
                    g.setMinMembers(app.getMinGroupMembers());
                    g.setAdminNick(client.getUser().getNick());

                    app.addGroup(g);
                    groups.put(g.getGid(), g);

                    socket.publishEvent(wampApp.getTopic(getFQtopicURI("app_event:" + appId)), listGroups(appId), false);  // don't exclude Me

                    valid = true;
                    created = true;
                }

            } catch(Exception err) {
                // valid = false;
            }

        }

        // generate response:
        JSONObject response = new JSONObject();
        response.put("cmd", "user_joined");

        if(valid) {
            Application app = g.getApplication();
            ArrayList<String> requiredRoles = new ArrayList<String>();
            
            response.put("created", created);
            response.put("gid", g.getGid());
            response.put("admin", g.getAdminNick());
            response.put("min", g.getMinMembers());
            response.put("max", g.getMaxMembers());
            response.put("observable", g.isObservableGroup());
            response.put("dynamic", g.isDynamicGroup());
            response.put("alliances", g.isAlliancesAllowed());

            response.put("app", app.toJSON());

            JSONArray rolesArray = new JSONArray();
            for(Role r : app.getRoles()) {
                rolesArray.put(r.toJSON());
                if(r.isRequired()) requiredRoles.add(0, r.getName());
            }
            response.put("roles", rolesArray);


            String topicName = getFQtopicURI("group_event:" + g.getGid());
            WampTopic topic = wampApp.getTopic(topicName);
            if(topic == null) topic = wampApp.createTopic(topicName);
            wampApp.subscribeClientWithTopic(client.getSocket(), topicName);
            
            client.addGroup(g);
            JSONArray conArray = new JSONArray();            
            for(String cid : topic.getSocketIds()) {
                    Client c = clients.get(cid);
                    User u = ((c!=null)? c.getUser() : null);
                    String nick = ((u == null) ? "" : u.getNick());

                    JSONObject con = new JSONObject();
                    con.put("nick", nick);
                    con.put("cid", cid);
                    conArray.put(con);
            }
            response.put("connections", conArray);            

            boolean reserved = false;
            int  reservedSlot = 0;
            int  num_slots = g.getNumSlots();
            int  avail_slots = 0;
            User currentUser = client.getUser();
            for(int index = 0;
                    (index < Math.max(num_slots, g.getMinMembers()));
                    index++) {
                
                GroupMember member = null;
                member = g.getMember(index);
                boolean connected = (member != null) && (member.getClient() != null);
                String nickName = ((member == null || member.getNick() == null) ? "" : member.getNick() );
                if(!connected && nickName == currentUser.getNick()) {
                    reserved = true;
                    reservedSlot = index;
                    break;
                } else if(!connected) {
                    avail_slots++;
                }
            }
            if(!reserved && avail_slots == 0 && num_slots < g.getMaxMembers()) {
                num_slots++;
            }

            JSONArray membersArray = new JSONArray();
            for(int index = 0;
                    (index < Math.max(num_slots, g.getMinMembers())) || (requiredRoles.size() > 0);
                    index++) {
                String usertype = "user";
                int team = 1+index;

                GroupMember member = g.getMember(index);
                if(member == null) member = new GroupMember();
                
                boolean connected = (member.getClient() != null);
                String roleName = (member.getRole() != null) ? member.getRole().toString() : "";
                if(roleName.length() > 0) {
                    requiredRoles.remove(roleName);
                } else if(requiredRoles.size() > 0) {
                    roleName = requiredRoles.remove(0);
                }

                if(!connected && !joined && (!reserved || index == reservedSlot)) {
                    member.setClient(client);
                    member.setNick(client.getUser().getNick());
                    member.setRole(g.getApplication().getRoleByName(roleName));
                    member.setUserType("user");
                    member.setTeam(1+index);
                    g.setMember(index, member);
                    client.setState(ClientState.JOINED);
                    joined = true;
                    connected = true;

                    JSONObject event = member.toJSON();
                    event.put("cmd", "user_joined");
                    event.put("slot", index);
                    event.put("valid", true);

                    socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+g.getGid())), event, true);  // exclude Me
                }


                JSONObject memberObj = member.toJSON();
                memberObj.put("slot", index);
                memberObj.put("connected", connected);
                
                membersArray.put(memberObj);
            }

            response.put("members", membersArray);
        }

        
        if(valid && !created && !joined) {
            User u = client.getUser();
            String cid = client.getClientId();
            String nickName = ( (u == null) ? "" : u.getNick() );

            JSONObject event = new JSONObject();
            event.put("cmd", "user_joined");
            event.put("nick", nickName);
            event.put("cid", cid);
            event.put("type", "user");
            event.put("valid", valid);
                    
            socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+g.getGid())), event, true);  // exclude Me
        }
        
        return response;
    }
    

    private JSONObject updateGroup(WampSocket socket, JSONObject data) throws Exception
    {
        // TODO: change group properties (state, observable, etc)

        boolean valid = false;
        String appId = data.getString("app");
        String gid = data.getString("gid");

        JSONObject response = new JSONObject();
        response.put("cmd", "update_group");
        
        
        Group g = groups.get(gid);
        if(g != null) {
            logger.log(Level.FINE, "open_group: group found: " + gid);
            
            String state = data.optString("state");
            if(state != null) {
                g.setState(GroupState.valueOf(state));
                response.put("state", state);
            }
                    
            valid = true;
        }

        response.put("valid", valid);

        if(valid) socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+g.getGid())), response, true);  // exclude Me
        return response;
    }
    
    
    private JSONObject updateMember(WampSocket socket, JSONObject data) throws Exception
    {
            boolean valid = false;
            String appId = data.getString("app");
            String gid = data.getString("gid");

            int slot = data.getInt("slot");
            String cid = data.getString("cid");
            String nick = data.getString("nick");
            String role = data.getString("role");
            String usertype = data.getString("type");
            int team = data.getInt("team");

            JSONObject response = new JSONObject();
            response.put("cmd", "user_joined");

            Group g = groups.get(gid);
            if(g != null) {
                valid = true;
                logger.log(Level.FINE, "open_group: group found: " + gid);

                Client c = clients.get(cid);
                if(c!=null) {
                    // when it's not a reservation of a member slot
                    User u = c.getUser();
                    if(u!=null) nick = u.getNick();
                }

                Role r = g.getApplication().getRoleByName(role);

                // TODO: check "slot" is valid
                GroupMember member = g.getMember(slot);
                if(member == null) member = new GroupMember();
                member.setClient(c);
                member.setNick(nick);
                member.setUserType(usertype);
                member.setRole(r);
                member.setTeam(team);
                g.setMember(slot, member);


                response.put("cid", cid);
                response.put("nick", nick);
                response.put("type", usertype);
                response.put("slot", slot);
                response.put("role", role);
                response.put("team", team);

            }

            response.put("valid", valid);

            if(valid) socket.publishEvent(wampApp.getTopic(getFQtopicURI("group_event:"+g.getGid())), response, true);  // exclude Me
            return response;
    }
    
    
    private JSONObject exitGroup(WampSocket socket, String gid) throws Exception
    {
            Client client = clients.get(socket.getSessionId());
            
            JSONObject response = new JSONObject();
            response.put("cmd", "user_unjoined");

            Group g = groups.get(gid);

            if(g != null) {
                String appId = g.getApplication().getAppId();
                logger.log(Level.FINE, "open_group: group found: " + gid);

                response.put("gid", g.getGid());
                response.put("cid", socket.getSessionId());

                int num_members = 0;
                JSONArray membersArray = new JSONArray();
                for(int slot = g.getNumSlots(); slot > 0; ) {
                    slot = slot-1;
                    GroupMember member = g.getMember(slot);
                    boolean connected = (member.getClient() != null);
                    if(connected) {
                        if(client == member.getClient()) {
                            logger.log(Level.INFO, "clearing slot " + slot);

                            member.setClient(null);
                            member.setNick(null);
                            member.setUserType("user");
                            g.setMember(slot, member);
                            
                            JSONObject obj = member.toJSON();
                            obj.put("slot", slot);
                            membersArray.put(obj);

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
                WampTopic topic = wampApp.unsubscribeClientFromTopic(socket, topicName);
                if(topic.getSocketCount() == 0) {

                    logger.log(Level.INFO, "closing group {0}: {1}", new Object[]{ g.getGid(), g.getDescription()});

                    groups.remove(g.getGid());
                    applications.get(appId).removeGroup(g);

                    wampApp.deleteTopic(topicName);
                    socket.publishEvent(wampApp.getTopic(getFQtopicURI("app_event:"+appId)), listGroups(appId), false);  // don't exclude Me
                }
            }


            return response;
    }


}
