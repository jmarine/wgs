package org.wgs.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import javax.json.*;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import org.wgs.security.OpenIdConnectClient;
import org.wgs.security.OpenIdConnectClientPK;
import org.wgs.security.User;
import org.wgs.security.UserPushChannel;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.type.WampDict;

import com.google.android.gcm.server.*;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;


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
        //if(usr.getTokenCaducity().before(Calendar.getInstance())) {
        //    accessToken = refreshToken(usr);
        //}


        URL url = new URL("https://www.googleapis.com/plus/v1/people/"+URLEncoder.encode(usr.getLogin(),StandardCharsets.UTF_8.toString())+"/people/visible");
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setDoOutput(false);

        try(JsonReader jsonReader = Json.createReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            
            JsonObject dataNode = jsonReader.readObject();
            
            JsonArray items = dataNode.getJsonArray("items");
            for(int index = 0; index < items.size(); index++) {
                JsonObject item = items.getJsonObject(index);
                String login = item.getString("id");
                List<User> found = Storage.findEntities(User.class, "wgs.findUsersByLoginAndDomain", login, domain);
                User friend = (found != null && found.size() > 0)? found.get(0) : null;
                if(friend != null && !friends.contains(friend)) {
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
        //if(usr.getTokenCaducity().before(Calendar.getInstance())) {
        //    accessToken = refreshToken(usr);
        //}


        String nextPage = "https://graph.facebook.com/v2.8/" + usr.getLogin() + "/friends";
        do {
            if(!nextPage.contains("access_token")) {
                if(!nextPage.contains("?")) nextPage += "?";
                else nextPage += "&";
                nextPage = nextPage + "access_token="+URLEncoder.encode(accessToken,StandardCharsets.UTF_8.toString());
            }
            
            System.out.println("Facebook CALL: " + nextPage);
            URL url = new URL(nextPage);
            nextPage = null;
            
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setDoOutput(false);

            try(JsonReader jsonReader = Json.createReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

                JsonObject friendsNode = jsonReader.readObject();

                JsonArray items = friendsNode.getJsonArray("data");
                for(int index = 0; index < items.size(); index++) {
                    JsonObject item = items.getJsonObject(index);
                    String login = item.getString("id");
                    List<User> found = Storage.findEntities(User.class, "wgs.findUsersByLoginAndDomain", login, domain);
                    User friend = (found != null && found.size() > 0)? found.get(0) : null;
                    if(friend != null && !friends.contains(friend)) {
                        friends.add(friend);
                    }
                }
                
                JsonObject pagingNode = friendsNode.getJsonObject("paging");
                nextPage = (pagingNode.containsKey("next")) ? pagingNode.getString("next") : null;
                
            } catch(Exception e) {
                
                System.err.println("Social.getFacebookFriends: Error: " + e.getClass().getName() + ":" + e.getMessage());
                e.printStackTrace();

            } finally {
                connection.disconnect();
            }
        
        } while(nextPage != null);

        usr.setFriends(friends);
        usr = Storage.saveEntity(usr);

        return friends;
    }
    
    
    
    private static String getFacebookAppAccessToken(String clientId, String secret) throws Exception
    {
        return clientId + "|" + secret;
    }

    
    public static void setUserPushChannel(User user, String appClientName, String notificationChannel)
    {
        UserPushChannel channel = new UserPushChannel();
        channel.setAppClientName(appClientName);
        channel.setUser(user);
        channel.setNotificationChannel(notificationChannel);
        if(notificationChannel != null) {
            Storage.saveEntity(channel);        
        } else {
            Storage.removeEntity(channel);
        }
    }
    
    public static void notifyUser(String app, WampSocket fromClientSocket, User toUser, String gameGuid, String template) throws Exception
    {
        User fromUser = (User)fromClientSocket.getUserPrincipal();
        template = template.replace("%me%", fromUser.getName());

        EntityManager manager = null;
        try {

            manager = Storage.getEntityManager();

            TypedQuery<UserPushChannel> queryProvider = manager.createNamedQuery("UserPushChannel.findByAppAndUser", UserPushChannel.class);
            queryProvider.setParameter(1, app);
            queryProvider.setParameter(2, toUser);

            UserPushChannel userPushChannel = null;
            try {
                userPushChannel = queryProvider.getSingleResult();
            } catch(NoResultException noEx) { }

            if(userPushChannel != null) {
                System.out.println("Sending notification to " + toUser.getName());
                String notificationChannel = userPushChannel.getNotificationChannel();
                WampDict info = (WampDict)WampEncoding.JSON.getSerializer().deserialize(notificationChannel, 0, notificationChannel.length());
                String endpoint = info.getText("endpoint");
                if(endpoint != null) {
                    if(endpoint.startsWith("https://updates.push.services.mozilla.com/")) {
                        
                        notifyWithMozillaPushService(app, endpoint, "{ \"provider\": \""+toUser.getDomain()+"\", \"gid\": \"" + gameGuid + "\"}", template);
                        
                    } else if(endpoint.startsWith("https://android.googleapis.com/gcm/send")) {
                        
                        String subscriptionId = info.getText("subscriptionId");
                        if(subscriptionId == null) {
                            subscriptionId = endpoint.substring(endpoint.lastIndexOf("/")+1);
                        }
                        notifyWithGCM(app, subscriptionId, "{ \"provider\": \""+toUser.getDomain()+"\", \"gid\": \"" + gameGuid + "\"}", template);
                        
                    }
                }

            } else {
                // SEND E-MAIL?  // but e-mail address are not verified, yet
            }

            if("facebook.com".equals(toUser.getDomain()) && fromUser.getDomain().equals(toUser.getDomain())) {
                System.out.println("Sending Facebook game activity to " + toUser.getName());
                OpenIdConnectClientPK oidcPK = new OpenIdConnectClientPK(toUser.getDomain(), app);
                OpenIdConnectClient oidcClient = manager.find(OpenIdConnectClient.class, oidcPK);
                notifyWithFacebook(oidcClient, toUser.getLogin(), "?provider=facebook.com&gid=" + gameGuid, template);
            } 
            
                
        } finally {
            if(manager != null) {
                try { manager.close(); }
                catch(Exception ex) { }
            }
        }
    }
    
    
    public static void notifyWithMozillaPushService(String app, String endpoint, String rel_link, String template) throws Exception
    {
        URL url = new URL(endpoint); 
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("POST");                // PUT method doesn't work with FireFox 49
        connection.setRequestProperty("TTL", "5184000");    // 1 month. Default TTL=0 means the message is discarded if the recipient is not actively connected
        connection.setDoOutput(false);

        try(BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line = null;
            while((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            
        } finally {
            connection.disconnect();
        }
    }
    
    public static void notifyWithGCM(String app, String registrationId, String rel_link, String template) throws Exception
    {
        File gcmKeyFile = new File(new File(System.getProperty("user.dir")).getParentFile(), "GCM-" + app.replace(' ', '_') + ".key");
        System.out.println("Searching GCM key in: " + gcmKeyFile.getAbsolutePath());
        try(BufferedReader reader = new BufferedReader(new FileReader(gcmKeyFile))) {
            String gcmKey = reader.readLine();
            Sender sender = new Sender(gcmKey);
            Message msg = new Message.Builder().addData("data", template).build();
            Result result = sender.send(msg, registrationId, 3);
            System.out.println("GCM: Sent message to one device: " + result);
        } 
        
    }
    
    public static void notifyWithFacebook(OpenIdConnectClient oidcClient, String to_user_id, String rel_link, String template) throws Exception
    {
        String appToken = getFacebookAppAccessToken(oidcClient.getClientId(), oidcClient.getClientSecret());
        URL url = new URL("https://graph.facebook.com/v2.8/" + to_user_id + "/apprequests?access_token="+ appToken + "&message=" + URLEncoder.encode(template,StandardCharsets.UTF_8.toString()) + "&data=" + URLEncoder.encode(rel_link,StandardCharsets.UTF_8.toString())); 
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(false);
        
        try(JsonReader jsonReader = Json.createReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
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
        URL url = new URL("https://graph.facebook.com/v2.8/me/apprequests?access_token="+ URLEncoder.encode(usr.getAccessToken(),StandardCharsets.UTF_8.toString()));
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setDoOutput(false);
        
        try(JsonReader jsonReader = Json.createReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            JsonObject dataNode = jsonReader.readObject();  
            System.out.println("Notification result: " + dataNode.toString());
            
            JsonArray  notifications = dataNode.getJsonArray("data");
            for(int i = 0; i < notifications.size(); i++) {
                JsonObject notification = notifications.getJsonObject(i);
                String id = notification.getString("id");
                JsonObject application = notification.getJsonObject("application");
                if(application.getString("id").equalsIgnoreCase(oidcClient.getClientId())) {
                
                    System.out.println("Deleting : " + id);
                    url = new URL("https://graph.facebook.com/v2.8/" + id + "?access_token="+ URLEncoder.encode(usr.getAccessToken(),StandardCharsets.UTF_8.toString()));
                    HttpURLConnection connection2 = (HttpURLConnection)url.openConnection();
                    connection2.setRequestMethod("DELETE");
                    connection2.setDoOutput(false);

                    try(JsonReader jsonReader2 = Json.createReader(new InputStreamReader(connection2.getInputStream(), StandardCharsets.UTF_8))) {
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
            notifyWithFacebook(oidcClient, "", "?1234", "It's your turn");  
    }
    
}
