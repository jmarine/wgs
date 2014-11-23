package org.wgs.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import javax.json.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.persistence.EntityManager;

import org.wgs.security.OpenIdConnectClient;
import org.wgs.security.OpenIdConnectClientPK;
import org.wgs.security.User;
import org.wgs.wamp.WampSocket;


public class Social 
{
    public static List<User> getFriends(User usr) throws Exception
    {
        List<User> friends = usr.getFriends();
        if(friends == null) {
            friends = new ArrayList<User>();
            usr.setFriends(friends);
        }

        String domain = usr.getDomain();
        if(domain.endsWith("facebook.com")) {
            return getFacebookFriends(usr);
        } else if(domain.endsWith("google.com")) {
            return getGoogleFriends(usr);
        } else {
            return usr.getFriends();
        }
    }
    
    public static List<User> getGoogleFriends(User usr) throws Exception
    {
        String domain = usr.getDomain();
        List<User> friends = usr.getFriends();

        // Google Plus
        String accessToken = usr.getAccessToken();
        if(usr.getTokenCaducity().before(Calendar.getInstance())) {
            // accessToken = refreshToken(usr);
        }


        URL url = new URL("https://www.googleapis.com/plus/v1/people/"+URLEncoder.encode(usr.getLogin(),"utf8")+"/people/visible");
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setDoOutput(false);

        try(JsonReader jsonReader = Json.createReader(connection.getInputStream())) {
            
            JsonObject dataNode = jsonReader.readObject();
            
            JsonArray items = dataNode.getJsonArray("items");
            for(int index = 0; index < items.size(); index++) {
                JsonObject item = items.getJsonObject(index);
                String login = item.getString("id");
                List<User> found = Storage.findEntities(User.class, "wgs.findUsersByLoginAndDomain", login, domain);
                User friend = (found != null && found.size() > 0)? found.get(0) : null;
                if(friend == null) {
                    friend = new User();
                    friend.setUid(UUID.randomUUID().toString());
                    friend.setLogin(login);
                    friend.setDomain(domain);
                    friend.setName(item.getString("displayName"));
                    friend.setAdministrator(false);
                    friend.setPicture(item.getJsonObject("image").getString("url"));
                    friend = Storage.saveEntity(friend);
                }
                if(!friends.contains(friend)) {
                    friends.add(friend);
                }
            }
            
        } finally {
            connection.disconnect();
        }

        usr.setFriends(friends);
        usr = Storage.saveEntity(usr);

        return friends;
    }

    
    public static List<User> getFacebookFriends(User usr) throws Exception
    {
        String domain = usr.getDomain();
        List<User> friends = usr.getFriends();

        // Google Plus
        String accessToken = usr.getAccessToken();
        if(usr.getTokenCaducity().before(Calendar.getInstance())) {
            // accessToken = refreshToken(usr);
        }


        URL url = new URL("https://graph.facebook.com/" + usr.getLogin() + "/friends?access_token="+URLEncoder.encode(accessToken,"utf8"));
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setDoOutput(false);

        try(JsonReader jsonReader = Json.createReader(connection.getInputStream())) {
            
            JsonObject dataNode = jsonReader.readObject();

            JsonArray items = dataNode.getJsonArray("data");
            for(int index = 0; index < items.size(); index++) {
                JsonObject item = items.getJsonObject(index);
                String login = item.getString("id");
                List<User> found = Storage.findEntities(User.class, "wgs.findUsersByLoginAndDomain", login, domain);
                User friend = (found != null && found.size() > 0)? found.get(0) : null;
                if(friend == null) {
                    friend = new User();
                    friend.setUid(UUID.randomUUID().toString());
                    friend.setLogin(login);
                    friend.setDomain(domain);
                    friend.setName(item.getString("name"));
                    friend.setAdministrator(false);
                    friend.setPicture("https://graph.facebook.com/"+login+"/picture");
                    friend = Storage.saveEntity(friend);
                }
                if(!friends.contains(friend)) {
                    friends.add(friend);
                }
            }
            
        } finally {
            connection.disconnect();
        }


        usr.setFriends(friends);
        usr = Storage.saveEntity(usr);

        return friends;
    }
    
    
    
    private static String getFacebookAppAccessToken(String clientId, String secret) throws Exception
    {
        return clientId + "|" + secret;
    }

    
    public static void notifyUser(WampSocket fromClientSocket, User toUser, String gameGuid, String template) throws Exception
    {
        User fromUser = (User)fromClientSocket.getUserPrincipal();
        
        if("facebook.com".equals(toUser.getDomain()) && fromUser.getDomain().equals(toUser.getDomain())) {
            String clientName = fromClientSocket.getHelloDetails().getText("_oauth2_client_name");
            template = template.replace("%me%", fromUser.getName()); // "@[" + fromUser.getLogin() + "]");
                    
            EntityManager manager = null;
            try {
                manager = Storage.getEntityManager();
                OpenIdConnectClientPK oidcPK = new OpenIdConnectClientPK(fromUser.getDomain(), clientName);
                OpenIdConnectClient oidcClient = manager.find(OpenIdConnectClient.class, oidcPK);
                notifyFacebookUser(oidcClient, toUser.getLogin(), "?provider=facebook.com&gid=" + gameGuid, template);
            } finally {
                if(manager != null) {
                    try { manager.close(); }
                    catch(Exception ex) { }
                }
            }
            
        } else {
            // SEND E-MAIL?  // but e-mail address are not verified, yet
        }
    }
    
    public static void notifyFacebookUser(OpenIdConnectClient oidcClient, String to_user_id, String rel_link, String template) throws Exception
    {
        String appToken = getFacebookAppAccessToken(oidcClient.getClientId(), oidcClient.getClientSecret());
        URL url = new URL("https://graph.facebook.com/v2.1/" + to_user_id + "/apprequests?access_token="+ appToken + "&message=" + URLEncoder.encode(template,"utf8") + "&data=" + URLEncoder.encode(rel_link,"utf8")); 
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(false);
        
        try(JsonReader jsonReader = Json.createReader(connection.getInputStream())) {
            JsonObject dataNode = jsonReader.readObject();       
            System.out.println("Notification result: " + dataNode.toString());
        } finally {
            connection.disconnect();
        }
    }
    
    
    
    public static void clearNotifications(OpenIdConnectClient oidcClient, User usr) throws Exception    
    {
        if(usr != null && usr.getDomain().equals("facebook.com")) {
            clearFacebookNotifications(oidcClient, usr);
        }
    }
    
    public static void clearFacebookNotifications(OpenIdConnectClient oidcClient, User usr) throws Exception
    {
        URL url = new URL("https://graph.facebook.com/v2.1/me/apprequests?access_token="+ URLEncoder.encode(usr.getAccessToken(), "utf8"));
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setDoOutput(false);
        
        try(JsonReader jsonReader = Json.createReader(connection.getInputStream())) {
            JsonObject dataNode = jsonReader.readObject();  
            System.out.println("Notification result: " + dataNode.toString());
            
            JsonArray  notifications = dataNode.getJsonArray("data");
            for(int i = 0; i < notifications.size(); i++) {
                JsonObject notification = notifications.getJsonObject(i);
                String id = notification.getString("id");
                JsonObject application = notification.getJsonObject("application");
                if(application.getString("id").equalsIgnoreCase(oidcClient.getClientId())) {
                
                    System.out.println("Deleting : " + id);
                    url = new URL("https://graph.facebook.com/v2.1/" + id + "?access_token="+ URLEncoder.encode(usr.getAccessToken(), "utf8"));
                    HttpURLConnection connection2 = (HttpURLConnection)url.openConnection();
                    connection2.setRequestMethod("DELETE");
                    connection2.setDoOutput(false);

                    try(JsonReader jsonReader2 = Json.createReader(connection2.getInputStream())) {
                        JsonObject success = jsonReader2.readObject();
                        System.out.println("Readed: " + success.toString());
                    } catch(Exception ex) { }

                }
            }
        } finally {
            connection.disconnect();
        }
    }    
    
    public static final void doFacebookTest() throws Exception
    {
            OpenIdConnectClientPK oidcClientPK = new OpenIdConnectClientPK("facebook.com", "WebGL 8x8 board games");
            OpenIdConnectClient oidcClient = Storage.findEntity(OpenIdConnectClient.class, oidcClientPK);
            notifyFacebookUser(oidcClient, "", "?1234", "It's your turn");  
    }
    
}
