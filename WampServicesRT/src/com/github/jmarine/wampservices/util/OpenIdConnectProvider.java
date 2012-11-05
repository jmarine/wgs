package com.github.jmarine.wampservices.util;

import java.io.*;
import java.net.*;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;


@Entity
@Table(name="OIC_PROVIDER")
public class OpenIdConnectProvider implements Serializable
{
    @EmbeddedId
    private OpenIdConnectProviderId id;

    @Column(name="client_id")
    private String clientId;
    
    @Column(name="client_secret")
    private String clientSecret;

    @Column(name="access_token_url")
    private String accessTokenEndpointUrl;
    
    @Column(name="userinfo_url")
    private String userInfoEndpointUrl;
    

    /**
     * @return the id
     */
    public OpenIdConnectProviderId getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(OpenIdConnectProviderId id) {
        this.id = id;
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
    public void setUserInfoEndpointUrl(String userInfoEndpointUrl) {
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
    public void setAccessTokenEndpointUrl(String accessTokenEndpointUrl) {
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
    public void setClientId(String clientId) {
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
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }


    
    

    public String getAccessTokenResponse(String authorization_code) throws Exception
    {
        URL url = new URL(getAccessTokenEndpointUrl());
        URLConnection connection = url.openConnection();
        connection.setDoOutput(true);

        OutputStreamWriter out = new OutputStreamWriter(
                                         connection.getOutputStream());
        out.write("grant_type=authorization_code&code=" + URLEncoder.encode(authorization_code) + "&client_id=" + getClientId() + "&redirect_uri=" + URLEncoder.encode(getId().getRedirectUri()) + "&client_id=" + URLEncoder.encode(getClientId()) + "&client_secret=" + URLEncoder.encode(getClientSecret()));
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
