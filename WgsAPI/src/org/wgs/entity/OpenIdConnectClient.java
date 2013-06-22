package org.wgs.entity;

import java.io.*;
import java.net.*;
import java.util.Calendar;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
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
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;


@Entity
@Table(name="OIC_CLIENT")
@NamedQueries({
  @NamedQuery(name="oic_client.findByRedirectUri", query="SELECT OBJECT(p) FROM OpenIdConnectClient p WHERE p.redirectUri like :uri")
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
    
    
    public void load(ObjectNode oicClientRegistrationResponse) {
        this.setRedirectUri(redirectUri);
        this.setClientId(oicClientRegistrationResponse.get("client_id").asText());
        this.setClientSecret(oicClientRegistrationResponse.get("client_secret").asText());
        this.setRegistrationClientUri(oicClientRegistrationResponse.get("registration_client_uri").asText());
        this.setRegistrationAccessToken(oicClientRegistrationResponse.get("registration_access_token").asText());                        

        Calendar expiration = null;
        if(oicClientRegistrationResponse.has("expires_at")) {
            long expires_at = oicClientRegistrationResponse.get("expires_at").asLong();
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
        ObjectMapper mapper = new ObjectMapper();
        URL url = new URL(this.getRegistrationClientUri());
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestProperty("Authorization", "Bearer " + this.getRegistrationAccessToken());
        
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
        Calendar expiration = null;
        if(oicClient.has("expires_at")) {
            long expires_at = oicClient.get("expires_at").asLong();
            if(expires_at != 0l) {
                expiration = Calendar.getInstance();
                expiration.setTimeInMillis(oicClient.get("expires_at").asLong()*1000);
            }
        }
        
        setClientId(oicClient.get("client_id").asText());
        if(oicClient.has("client_secret")) setClientSecret(oicClient.get("client_secret").asText());
        if(oicClient.has("registration_client_uri")) setRegistrationClientUri(oicClient.get("registration_client_uri").asText());
        if(oicClient.has("registration_access_token")) setRegistrationAccessToken(oicClient.get("registration_access_token").asText());
        setClientExpiration(expiration);
    }
    
}
