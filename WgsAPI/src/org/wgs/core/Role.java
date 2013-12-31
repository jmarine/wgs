package org.wgs.core;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.wgs.wamp.WampDict;


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
    
    @Lob()
    @Column(name="icon")
    private String iconDataURL;
    

    /**
     * @return the application
     */
    public Application getApplication() 
    {
        return application;
    }

    /**
     * @param application the application to set
     */
    public void setApplication(Application application) 
    {
        this.application = application;
    }    
    
    /**
     * @return the name
     */
    public String getName() 
    {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) 
    {
        this.name = name;
    }

    /**
     * @return the required
     */
    public boolean isRequired() 
    {
        return required;
    }

    /**
     * @param required the required to set
     */
    public void setRequired(boolean required) 
    {
        this.required = required;
    }

    /**
     * @return the multiple
     */
    public boolean isMultiple() 
    {
        return multiple;
    }

    /**
     * @param multiple the multiple to set
     */
    public void setMultiple(boolean multiple) 
    {
        this.multiple = multiple;
    }
    
    /**
     * @return the iconDataURL
     */
    public String getIconDataURL() 
    {
        return iconDataURL;
    }

    /**
     * @param iconDataURL the iconDataURL to set
     */
    public void setIconDataURL(String iconDataURL) 
    {
        this.iconDataURL = iconDataURL;
    }    

    
    public WampDict toWampObject() 
    {
        WampDict obj = new WampDict();
        obj.put("name", name);
        obj.put("required", required);
        obj.put("multiple", multiple);
        obj.put("iconDataURL", iconDataURL);
        return obj;
    }

    @Override
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
