package org.wgs.util;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import javax.json.*;

import org.wgs.security.User;


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
    
    
}
