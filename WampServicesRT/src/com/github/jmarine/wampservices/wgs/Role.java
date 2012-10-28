/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.jmarine.wampservices.wgs;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;

/**
 *
 * @author jordi
 */
@Entity
@Table(name="APP_ROLE")
public class Role implements Serializable 
{
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="app")
    private Application application;

    @Id
    @Column(name="name")
    private String  name;
    
    @Column(name="required")
    private boolean required;
    
    @Column(name="multiple")
    private boolean multiple;
    

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the required
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * @param required the required to set
     */
    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * @return the multiple
     */
    public boolean isMultiple() {
        return multiple;
    }

    /**
     * @param multiple the multiple to set
     */
    public void setMultiple(boolean multiple) {
        this.multiple = multiple;
    }

    /**
     * @return the app
     */
    public Application getApplication() {
        return application;
    }

    /**
     * @param app the app to set
     */
    public void setApplication(Application app) {
        this.application = app;
    }
    
    public ObjectNode toJSON() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode obj = mapper.createObjectNode();
        obj.put("name", name);
        obj.put("required", required);
        obj.put("multiple", multiple);
        return obj;
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if(required && multiple) sb.append("+");
        if(!required) {
            if(multiple) sb.append("*");
            else sb.append("?");
        }
        return sb.toString();
        
    }
}
