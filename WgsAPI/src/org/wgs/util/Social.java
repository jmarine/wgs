package org.wgs.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;

import org.wgs.entity.User;


public class Social 
{
    public static List<User> getFriends(User usr) throws Exception
    {
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

        StringBuffer data = new StringBuffer();
        URL url = new URL("https://www.googleapis.com/plus/v1/people/"+URLEncoder.encode(usr.getLogin(),"utf8")+"/people/visible");
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setDoOutput(false);

        BufferedReader in = new BufferedReader(
                                    new InputStreamReader(
                                    connection.getInputStream()));
        String decodedString;
        while ((decodedString = in.readLine()) != null) {
            data.append(decodedString);
        }
        in.close();
        connection.disconnect();

        ObjectMapper mapper = new ObjectMapper();            
        ObjectNode dataNode = (ObjectNode) mapper.readTree(data.toString());

        ArrayNode items = (ArrayNode)dataNode.get("items");
        for(int index = 0; index < items.size(); index++) {
            ObjectNode item = (ObjectNode)items.get(index);
            String login = item.get("id").asText();
            List<User> found = Storage.findEntities(User.class, "wgs.findUsersByLoginAndDomain", login, domain);
            User friend = (found != null && found.size() > 0)? found.get(0) : null;
            if(friend == null) {
                friend = new User();
                friend.setUid(UUID.randomUUID().toString());
                friend.setLogin(login);
                friend.setDomain(domain);
                friend.setName(item.get("displayName").asText());
                friend.setAdministrator(false);
                friend.setPicture(item.get("image").get("url").asText());
                friend = Storage.saveEntity(friend);
            }
            if(!friends.contains(friend)) {
                friends.add(friend);
            }
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

        StringBuffer data = new StringBuffer();
        URL url = new URL("https://graph.facebook.com/" + usr.getLogin() + "/friends?access_token="+URLEncoder.encode(accessToken,"utf8"));
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setDoOutput(false);

        BufferedReader in = new BufferedReader(
                                    new InputStreamReader(
                                    connection.getInputStream()));
        String decodedString;
        while ((decodedString = in.readLine()) != null) {
            data.append(decodedString);
        }
        in.close();
        connection.disconnect();

        ObjectMapper mapper = new ObjectMapper();            
        ObjectNode dataNode = (ObjectNode) mapper.readTree(data.toString());

        ArrayNode items = (ArrayNode)dataNode.get("data");
        for(int index = 0; index < items.size(); index++) {
            ObjectNode item = (ObjectNode)items.get(index);
            String login = item.get("id").asText();
            List<User> found = Storage.findEntities(User.class, "wgs.findUsersByLoginAndDomain", login, domain);
            User friend = (found != null && found.size() > 0)? found.get(0) : null;
            if(friend == null) {
                friend = new User();
                friend.setUid(UUID.randomUUID().toString());
                friend.setLogin(login);
                friend.setDomain(domain);
                friend.setName(item.get("name").asText());
                friend.setAdministrator(false);
                friend.setPicture("https://graph.facebook.com/"+login+"/picture");
                friend = Storage.saveEntity(friend);
            }
            if(!friends.contains(friend)) {
                friends.add(friend);
            }
        }

        usr.setFriends(friends);
        usr = Storage.saveEntity(usr);

        return friends;
    }
    
    
}
