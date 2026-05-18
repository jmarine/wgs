package org.wgs.service.game;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampObject;


public class Card implements Serializable 
{
    private String type;
    private String value;
    

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * @param value the value to set
     */
    public void setValue(String value) {
        this.value = value;
    }
    
    @Override
    public boolean equals(Object o)
    {
        if(o != null && o instanceof Card) {
            Card c = (Card)o;
            return type.equals(c.type) && value.equals(c.value);
        } else {
            return false;
        }
    }
    
    @Override
    public String toString()
    {
        return value + "_" + type;
    }

    @Override
    public int hashCode()
    {
        return toString().hashCode();
    }
        
    public static Card parseCard(String str)
    {
        Card c = null;        
        if(str != null) {
            String parts[] = str.split("_");
            if(parts.length > 0) {            
                c = new Card();
                c.setValue(parts[0]);
                if(parts.length > 1) {
                    c.setType(parts[1]);
                }
            }
        }
        return c;
    }
    

}
