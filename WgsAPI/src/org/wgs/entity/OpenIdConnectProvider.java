package org.wgs.entity;

import java.io.*;
import java.net.*;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;


@Entity
@Table(name="OIDC_PROVIDER")
@NamedQueries({
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
        if(domain.endsWith("facebook.com")) scopes = "email,publish_actions";        
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

    public static ObjectNode discover(String principal) throws Exception
    {
        ObjectNode retval = null;
        if(principal.indexOf("#") != -1) principal = principal.substring(0, principal.indexOf("#"));
        if(principal.indexOf("?") != -1) principal = principal.substring(0, principal.indexOf("?"));
        
        String principalUrl = principal;
        if(principalUrl.indexOf("://") == -1) principalUrl = "https://" + principal;
        URL url = new URL(principalUrl);
        
        String webfingerRequestUrl = url.getProtocol() + "://" + url.getHost();
        if(url.getPort() != -1) webfingerRequestUrl += ":" + url.getPort();
        webfingerRequestUrl += "/.well-known/webfinger";

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode webfingerResponse = null;

        StringBuffer response = new StringBuffer();
        String paramSeparator = ((webfingerRequestUrl.indexOf("?") == -1)? "?":"&");
        webfingerRequestUrl += paramSeparator + "resource=" + URLEncoder.encode(principal,"utf8");
        webfingerRequestUrl += "&rel=" + URLEncoder.encode("http://openid.net/specs/connect/1.0/issuer","utf8");
        url = new URL(webfingerRequestUrl);

        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setDoOutput(false);

        BufferedReader in = new BufferedReader(
                                    new InputStreamReader(
                                    connection.getInputStream()));
        String decodedString;
        while ((decodedString = in.readLine()) != null) {
            response.append(decodedString);
        }
        in.close();
        connection.disconnect();

        webfingerResponse = (ObjectNode) mapper.readTree(response.toString());

        
        if(webfingerResponse.has("links")) {
            StringBuffer oicConfig = new StringBuffer();
            ArrayNode links = (ArrayNode)webfingerResponse.get("links");
            for(int index = 0; index < links.size(); index++) {
                ObjectNode link = (ObjectNode)links.get(index);
                String rel = link.get("rel").asText();
                if(rel.equals("http://openid.net/specs/connect/1.0/issuer")) {
                    url = new URL(link.get("href").asText() + "/.well-known/openid-configuration");
                    connection = (HttpURLConnection)url.openConnection();
                    connection.setDoOutput(false);

                    in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    while ((decodedString = in.readLine()) != null) {
                        oicConfig.append(decodedString);
                    }
                    in.close();
                    connection.disconnect();

                    retval = (ObjectNode) mapper.readTree(oicConfig.toString());
                    break;
                }
            }
        }
            
        return retval;
    }

    public ObjectNode registerClient(String appName, String redirectUri) throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode retval = null;
        URL url = new URL(getRegistrationEndpointUrl());
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");

        ObjectNode req = mapper.createObjectNode();
        ArrayNode redirect_uris = mapper.createArrayNode();
        redirect_uris.add(redirectUri);
        req.put("application_type", "web");
        req.put("redirect_uris", redirect_uris);
        req.put("client_name", appName);
        
        OutputStreamWriter out = new OutputStreamWriter(
                                         connection.getOutputStream());
        out.write(req.toString());
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
    
}
