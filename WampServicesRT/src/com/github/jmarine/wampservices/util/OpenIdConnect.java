package com.github.jmarine.wampservices.util;

import java.io.*;
import java.net.*;
import java.util.Properties;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;


public class OpenIdConnect
{
    private String userInfoEndpointUrl = "http://localhost:3000/user_info";
    private String accessTokenEndpointUrl = "http://localhost:3000/access_tokens";
    private String clientId = "";
    private String clientSecret = "";
    private String redirectUri = ""; 
    
    private OpenIdConnect() { }
            

    public static OpenIdConnect getClient(Properties appConfig, String provider)
    {
        OpenIdConnect client = new OpenIdConnect();
        client.setUserInfoEndpointUrl(getConfig(appConfig, provider, "userInfoEndpointUrl"));
        client.setAccessTokenEndpointUrl(getConfig(appConfig, provider, "accessTokenEndpointUrl"));
        client.setClientId(getConfig(appConfig, provider, "clientId"));
        client.setClientSecret(getConfig(appConfig, provider, "clientSecret"));
        client.setRedirectUri(getConfig(appConfig, provider, "redirectUri"));
        return client;
    }
    
    private static String getConfig(Properties appConfig, String provider, String key)
    {
        return appConfig.getProperty("openIdConnect." + provider + "." + key);
    }
    

    /**
     * @return the userInfoEndpointUrl
     */
    public String getUserInfoEndpointUrl() {
        return userInfoEndpointUrl;
    }

    /**
     * @param userInfoEndpointUrl the userInfoEndpointUrl to set
     */
    private void setUserInfoEndpointUrl(String userInfoEndpointUrl) {
        this.userInfoEndpointUrl = userInfoEndpointUrl;
    }

    /**
     * @return the accessTokenEndpointUrl
     */
    public String getAccessTokenEndpointUrl() {
        return accessTokenEndpointUrl;
    }

    /**
     * @param accessTokenEndpointUrl the accessTokenEndpointUrl to set
     */
    private void setAccessTokenEndpointUrl(String accessTokenEndpointUrl) {
        this.accessTokenEndpointUrl = accessTokenEndpointUrl;
    }

    /**
     * @return the clientId
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * @param clientId the clientId to set
     */
    private void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * @return the clientSecret
     */
    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * @param clientSecret the clientSecret to set
     */
    private void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    /**
     * @return the redirectUri
     */
    public String getRedirectUri() {
        return redirectUri;
    }

    /**
     * @param redirectUri the redirectUri to set
     */
    private void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }
    
    

    public String getAccessTokenResponse(String authorization_code) throws Exception
    {
        URL url = new URL(getAccessTokenEndpointUrl());
        URLConnection connection = url.openConnection();
        connection.setDoOutput(true);

        OutputStreamWriter out = new OutputStreamWriter(
                                         connection.getOutputStream());
        out.write("grant_type=authorization_code&code=" + URLEncoder.encode(authorization_code) + "&client_id=" + getClientId() + "&redirect_uri=" + URLEncoder.encode(getRedirectUri()) + "&client_id=" + URLEncoder.encode(getClientId()) + "&client_secret=" + URLEncoder.encode(getClientSecret()));
        out.close();

        BufferedReader in = new BufferedReader(
                                    new InputStreamReader(
                                    connection.getInputStream()));
        String decodedString;
        StringBuffer data = new StringBuffer();
        while ((decodedString = in.readLine()) != null) {
	    data.append(decodedString);
        }
        in.close();

        return data.toString();
    }
    
    
    public String getUserInfo(String accessToken) throws Exception
    {
        StringBuffer retval = new StringBuffer();
        URL url = new URL(getUserInfoEndpointUrl() + "?schema=openid");
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setDoOutput(false);

        BufferedReader in = new BufferedReader(
                                    new InputStreamReader(
                                    connection.getInputStream()));
        String decodedString;
        while ((decodedString = in.readLine()) != null) {
            retval.append(decodedString);
        }
        in.close();
        
        return retval.toString();
    }    
    
}
