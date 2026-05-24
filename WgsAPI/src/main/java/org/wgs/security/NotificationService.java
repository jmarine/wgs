package org.wgs.security;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;


@Entity
@Table(name="NOTIFICATION_SERVICE")
public class NotificationService implements Serializable
{
    @Id
    @Column(name="app_client_name")    
    private String appClientName;
    
    @Lob
    @Column(name = "private_key")
    private byte[] privateKey;
    
    @Lob
    @Column(name = "public_key")
    private byte[] publicKey;

    /**
     * @return the appClientName
     */
    public String getAppClientName() {
        return appClientName;
    }

    /**
     * @param appClientName the appClientName to set
     */
    public void setAppClientName(String appClientName) {
        this.appClientName = appClientName;
    }

    /**
     * @return the privateKey
     */
    public byte[] getPrivateKey() {
        return privateKey;
    }

    /**
     * @param privateKey the privateKey to set
     */
    public void setPrivateKey(byte[] privateKey) {
        this.privateKey = privateKey;
    }

    /**
     * @return the publicKey
     */
    public byte[] getPublicKey() {
        return publicKey;
    }

    /**
     * @param publicKey the publicKey to set
     */
    public void setPublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
    }
   
}