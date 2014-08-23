package org.wgs.security;

import java.io.*;
import java.net.*;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;


@Entity
@Table(name="OIDC_PROVIDER")
@NamedQueries({
  @NamedQuery(name="OpenIdConnectProvider.findAll", query="SELECT OBJECT(p) FROM OpenIdConnectProvider p"),
  @NamedQuery(name="OpenIdConnectProvider.findDynamic", query="SELECT OBJECT(p) FROM OpenIdConnectProvider p WHERE p.dynamic = true")
})
public class OpenIdConnectProvider implements Serializable
{
    @Id
    @Column(name="provider_domain")
    private String domain;
    
    @Column(name="dynamic", nullable=true)
    private boolean dynamic;
    
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
    public String getDomain() {
        return domain;
    }

    /**
     * @param id the id to set
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    public boolean getDynamic() 
    {
        return dynamic;
    }

    public void setDynamic(boolean dynamic) 
    {
        this.dynamic = dynamic;
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
    
    
    public String getScopes() 
    {
        String scopes = "openid profile email";
        if(domain.endsWith("facebook.com")) scopes = "email,publish_actions,manage_notifications";        
        if(domain.endsWith("google.com"))   scopes = "https://www.googleapis.com/auth/plus.login https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/gcm_for_chrome";
        return scopes;
    }
    

    public String getUserInfo(String accessToken) throws Exception
    {
        StringBuffer retval = new StringBuffer();
        String url = getUserInfoEndpointUrl();
        if(!getDomain().equalsIgnoreCase("www.facebook.com")) url = url + "?schema=openid";
        else url = url + "?access_token=" + URLEncoder.encode(accessToken, "utf-8");
        HttpURLConnection connection = (HttpURLConnection)(new URL(url)).openConnection();
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

    public static JsonObject discover(String principal) throws Exception
    {
        JsonObject retval = null;
        HttpURLConnection connection = null;
        if(principal.indexOf("#") != -1) principal = principal.substring(0, principal.indexOf("#"));
        if(principal.indexOf("?") != -1) principal = principal.substring(0, principal.indexOf("?"));
        int endSchemaPos = principal.indexOf("://");
        if(endSchemaPos!=-1 && principal.indexOf("/", endSchemaPos+3) == -1) {
            // Append slash to HOSTNAME[:PORT] resources
            principal = principal + "/";
        }
        
        String principalUrl = principal;
        if(principal.indexOf("://") == -1) {
            if(principal.indexOf("@") == -1) {
                principalUrl = "https://" + principal;
            } else {
                principal = "acct:" + principal;
                principalUrl = "https://" + principal.substring(1+principal.indexOf("@"));
            }
        }

        String webfingerPath = "/.well-known/webfinger";
        webfingerPath += "?resource=" + URLEncoder.encode(principal,"utf8");
        webfingerPath += "&rel=" + URLEncoder.encode("http://openid.net/specs/connect/1.0/issuer","utf8");

        URL webfingerUrl = new URL(new URL(principalUrl), webfingerPath);
        connection = (HttpURLConnection)webfingerUrl.openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setDoOutput(false);

        try(JsonReader jsonReader = Json.createReader(connection.getInputStream())) {
            
            JsonObject webfingerResponse = jsonReader.readObject();        
            
            if(webfingerResponse.containsKey("links")) {
                StringBuffer oidcConfig = new StringBuffer();
                JsonArray links = webfingerResponse.getJsonArray("links");
                for(int index = 0; index < links.size(); index++) {
                    JsonObject link = (JsonObject)links.get(index);
                    String rel = link.getString("rel");
                    if(rel.equals("http://openid.net/specs/connect/1.0/issuer")) {
                        webfingerUrl = new URL(link.getString("href"));
                        webfingerUrl = new URL(webfingerUrl, "/.well-known/openid-configuration");
                        HttpURLConnection connection2 = (HttpURLConnection)webfingerUrl.openConnection();
                        connection2.setDoOutput(false);

                        try(JsonReader jsonReader2 = Json.createReader(connection2.getInputStream())) {
                            retval = jsonReader2.readObject();
                        } finally {
                            connection2.disconnect();
                        }
                        break;
                    }
                }
            }
            
        } finally {
            connection.disconnect();        
        }
            
        return retval;
    }

    public JsonObject registerClient(String appName, String redirectUri) throws Exception
    {
        JsonObject retval = null;
        URL url = new URL(getRegistrationEndpointUrl());
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");

        JsonObject req = Json.createObjectBuilder()
                .add("application_type", "web")
                .add("redirect_uris", Json.createArrayBuilder().add(redirectUri))
                .add("client_name", appName)
                .build();
                
        
        OutputStreamWriter out = new OutputStreamWriter(
                                         connection.getOutputStream());
        out.write(req.toString());
        out.close();

        try(JsonReader jsonReader = Json.createReader(connection.getInputStream())) {
            
            retval = jsonReader.readObject();
            
        } finally {
            connection.disconnect();
        }

        return retval;
    }
    
}
