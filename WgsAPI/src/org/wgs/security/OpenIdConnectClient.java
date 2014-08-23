package org.wgs.security;

import java.io.*;
import java.net.*;
import java.util.Calendar;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.TemporalType;


@Entity
@Table(name="OIDC_CLIENT")
@NamedQueries({
  @NamedQuery(name="OpenIdConnectClient.findByRedirectUri", query="SELECT OBJECT(p) FROM OpenIdConnectClient p WHERE p.redirectUri like :uri")
})
@IdClass(OpenIdConnectClientPK.class)
public class OpenIdConnectClient implements Serializable
{
    @ManyToOne
    @Id
    @JoinColumn(name="provider_domain", referencedColumnName="provider_domain")
    private OpenIdConnectProvider provider;

    @Id
    @Column(name="redirect_url")
    private String redirectUri = "";

    @Column(name="client_id")
    private String clientId;
    
    @Column(name="client_secret")
    private String clientSecret;
    
    @javax.persistence.Temporal(TemporalType.TIMESTAMP)
    @Column(name="client_expiration")
    private java.util.Calendar clientExpiration;
    
    @Column(name="registration_client_uri")
    private String registrationClientUri;
    
    @Lob
    @Column(name="registration_access_token")
    private String registrationAccessToken;

    /**
     * @return the provider domain
     */
    public OpenIdConnectProvider getProvider() {
        return provider;
    }

    /**
     * @param providerDomain the provider domain to set
     */
    public void setProvider(OpenIdConnectProvider provider) {
        this.provider = provider;
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
    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
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
     * @return the registrationClientUri
     */
    public String getRegistrationClientUri() {
        return registrationClientUri;
    }

    /**
     * @param registrationClientUri the registrationClientUri to set
     */
    public void setRegistrationClientUri(String registrationClientUri) {
        this.registrationClientUri = registrationClientUri;
    }

    /**
     * @return the registrationAccessToken
     */
    public String getRegistrationAccessToken() {
        return registrationAccessToken;
    }

    /**
     * @param registrationAccessToken the registrationAccessToken to set
     */
    public void setRegistrationAccessToken(String registrationAccessToken) {
        this.registrationAccessToken = registrationAccessToken;
    }
    
    

    /**
     * @param clientExpiration the clientExpiration to set
     */
    public void setClientExpiration(java.util.Calendar clientExpiration) {
        this.clientExpiration = clientExpiration;
    }
    
    
    public void load(JsonObject oidcClientRegistrationResponse) {
        this.setRedirectUri(redirectUri);
        this.setClientId(oidcClientRegistrationResponse.getString("client_id"));
        this.setClientSecret(oidcClientRegistrationResponse.getString("client_secret"));
        this.setRegistrationClientUri(oidcClientRegistrationResponse.getString("registration_client_uri"));
        this.setRegistrationAccessToken(oidcClientRegistrationResponse.getString("registration_access_token"));

        Calendar expiration = null;
        if(oidcClientRegistrationResponse.containsKey("expires_at")) {
            long expires_at = oidcClientRegistrationResponse.getJsonNumber("expires_at").longValue();
            if(expires_at != 0l) {
                expiration = Calendar.getInstance();
                expiration.setTimeInMillis(expires_at*1000);
            }
        }                        
        this.setClientExpiration(expiration);
    }

    
    public String getAccessTokenResponse(String authorization_code) throws Exception
    {
        URL url = new URL(provider.getAccessTokenEndpointUrl());
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setDoOutput(true);

        OutputStreamWriter out = new OutputStreamWriter(
                                         connection.getOutputStream());
        out.write("grant_type=authorization_code&code=" + URLEncoder.encode(authorization_code,"utf8") + "&client_id=" + URLEncoder.encode(getClientId(),"utf8") + "&client_secret=" + URLEncoder.encode(getClientSecret(),"utf8") + "&redirect_uri=" + URLEncoder.encode(getRedirectUri(),"utf8") );
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
    

    public void updateClientCredentials() throws Exception
    {
        URL url = new URL(this.getRegistrationClientUri());
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestProperty("Authorization", "Bearer " + this.getRegistrationAccessToken());
        

        try(JsonReader jsonReader = Json.createReader(connection.getInputStream())) {
            
            JsonObject oidcClient = jsonReader.readObject();

            setClientId(oidcClient.getString("client_id"));
            if(oidcClient.containsKey("client_secret")) setClientSecret(oidcClient.getString("client_secret"));
            if(oidcClient.containsKey("registration_client_uri")) setRegistrationClientUri(oidcClient.getString("registration_client_uri"));
            if(oidcClient.containsKey("registration_access_token")) setRegistrationAccessToken(oidcClient.getString("registration_access_token"));

            Calendar expiration = null;
            if(oidcClient.containsKey("expires_at")) {
                long expires_at = oidcClient.getJsonNumber("expires_at").longValue();
                if(expires_at != 0l) {
                    expiration = Calendar.getInstance();
                    expiration.setTimeInMillis(expires_at*1000);
                }
            }
            setClientExpiration(expiration);

        } finally {
            connection.disconnect();
        }

        
    }
    
}
