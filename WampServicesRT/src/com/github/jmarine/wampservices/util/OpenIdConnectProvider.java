package com.github.jmarine.wampservices.util;

import java.io.*;
import java.net.*;
import java.util.Calendar;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.TemporalType;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;


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
    
    @javax.persistence.Temporal(TemporalType.TIMESTAMP)
    @Column(name="client_expiration")
    private java.util.Calendar clientExpiration;
    
    @Column(name="auth_endpoint_url")
    private String authEndpointUrl;

    @Column(name="access_token_url")
    private String accessTokenEndpointUrl;
    
    @Column(name="userinfo_url")
    private String userInfoEndpointUrl;
    
    @Column(name="registration_url")
    private String registrationEndpointUrl;    
    

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
     * @return the authEndpointUrl
     */
    public String getAuthEndpointUrl() {
        return authEndpointUrl;
    }

    /**
     * @param authEndpointUrl the authEndpointUrl to set
     */
    public void setAuthEndpointUrl(String authEndpointUrl) {
        this.authEndpointUrl = authEndpointUrl;
    }    

    /**
     * @return the registrationEndpointUrl
     */
    public String getRegistrationEndpointUrl() {
        return registrationEndpointUrl;
    }

    /**
     * @param registrationEndpointUrl the registrationEndpointUrl to set
     */
    public void setRegistrationEndpointUrl(String registrationEndpointUrl) {
        this.registrationEndpointUrl = registrationEndpointUrl;
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

    
    /**
     * @return the clientExpiration
     */
    public java.util.Calendar getClientExpiration() {
        return clientExpiration;
    }

    /**
     * @param clientExpiration the clientExpiration to set
     */
    public void setClientExpiration(java.util.Calendar clientExpiration) {
        this.clientExpiration = clientExpiration;
    }
    

    public String getAccessTokenResponse(String authorization_code) throws Exception
    {
        URL url = new URL(getAccessTokenEndpointUrl());
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setDoOutput(true);

        OutputStreamWriter out = new OutputStreamWriter(
                                         connection.getOutputStream());
        out.write("grant_type=authorization_code&code=" + URLEncoder.encode(authorization_code,"utf8") + "&client_id=" + URLEncoder.encode(getClientId(),"utf8") + "&redirect_uri=" + URLEncoder.encode(getId().getRedirectUri(),"utf8") + "&client_id=" + URLEncoder.encode(getClientId(),"utf8") + "&client_secret=" + URLEncoder.encode(getClientSecret(),"utf8"));
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
        connection.disconnect();

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
        connection.disconnect();
        
        return retval.toString();
    }    

    public static ObjectNode discover(String principal) throws Exception
    {
        ObjectNode retval = null;
        if(principal.indexOf("#") != -1) principal = principal.substring(0, principal.indexOf("#"));
        if(principal.indexOf("?") != -1) principal = principal.substring(0, principal.indexOf("?"));
        
        String principalUrl = principal;
        if(principalUrl.indexOf("://") == -1) principalUrl = "https://" + principal;
        URL url = new URL(principalUrl);
        
        String swdRequestUrl = url.getProtocol() + "://" + url.getHost();
        if(url.getPort() != -1) swdRequestUrl += ":" + url.getPort();
        swdRequestUrl += "/.well-known/simple-web-discovery";

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode swd = null;
        while(swdRequestUrl != null) {
            StringBuffer swdResponse = new StringBuffer();
            String paramSeparator = ((swdRequestUrl.indexOf("?") == -1)? "?":"&");
            swdRequestUrl += paramSeparator + "principal=" + URLEncoder.encode(principal,"utf8");
            swdRequestUrl += "&service=" + URLEncoder.encode("http://openid.net/specs/connect/1.0/issuer","utf8");
            url = new URL(swdRequestUrl);
        
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setDoOutput(false);

            BufferedReader in = new BufferedReader(
                                        new InputStreamReader(
                                        connection.getInputStream()));
            String decodedString;
            while ((decodedString = in.readLine()) != null) {
                swdResponse.append(decodedString);
            }
            in.close();
            connection.disconnect();

            swd = (ObjectNode) mapper.readTree(swdResponse.toString());
            if(swd.has("SWD_service_redirect")) {
                swdRequestUrl = swd.get("SWD_service_redirect").asText();
            } else {
                swdRequestUrl = null;
            }
        }
        
        if(swd.has("locations")) {
            StringBuffer oicConfig = new StringBuffer();
            ArrayNode locations = (ArrayNode)swd.get("locations");
            url = new URL(locations.get(0).asText() + "/.well-known/openid-configuration");
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setDoOutput(false);

            BufferedReader in = new BufferedReader(
                                        new InputStreamReader(
                                        connection.getInputStream()));
            String decodedString;
            while ((decodedString = in.readLine()) != null) {
                oicConfig.append(decodedString);
            }
            in.close();
            connection.disconnect();
            
            retval = (ObjectNode) mapper.readTree(oicConfig.toString());
        }
            
        return retval;
    }

    public static ObjectNode registerClient(String registrationEndpointUrl, String redirectUri, String appName) throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode retval = null;
        URL url = new URL(registrationEndpointUrl);
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setDoOutput(true);

        OutputStreamWriter out = new OutputStreamWriter(
                                         connection.getOutputStream());
        out.write("type=client_associate&application_type=web&application_name=" + URLEncoder.encode(appName,"utf8") + "&redirect_uris=" + URLEncoder.encode(redirectUri,"utf8"));
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
        connection.disconnect();

        retval = (ObjectNode) mapper.readTree(data.toString());
        return retval;
    }

    
    public void rotateClientCredentials() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        URL url = new URL(registrationEndpointUrl);
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setDoOutput(true);

        OutputStreamWriter out = new OutputStreamWriter(
                                         connection.getOutputStream());
        out.write("type=rotate_secret&client_id=" + URLEncoder.encode(this.getClientId(),"utf8")+ "&client_secret=" + URLEncoder.encode(this.getClientSecret(),"utf8"));
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
        connection.disconnect();

        ObjectNode oicClient = (ObjectNode) mapper.readTree(data.toString());
        Calendar clientExpiration = Calendar.getInstance();
        clientExpiration.setTimeInMillis(oicClient.get("expires_at").asLong()*1000);
        
        setClientId(oicClient.get("client_id").asText());
        setClientSecret(oicClient.get("client_secret").asText());
        setClientExpiration(clientExpiration);
    }
    
    
}
