package org.wgs.service.game;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.wgs.wamp.type.WampDict;


@Entity
@Table(name="APP_ROLE")
public class Role implements Serializable 
{
    private static final long serialVersionUID = 0L;
    
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
    public boolean equals(Object o)
    {
        if(o != null && o instanceof Role) {
            Role role = (Role)o;
            return name.equals(role.name) && (application.equals(role.application));
        } else {
            return false;
        }
    }
    
    
    @Override
    public int hashCode()
    {
        return (application.getName() + ":" + name).hashCode();
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
