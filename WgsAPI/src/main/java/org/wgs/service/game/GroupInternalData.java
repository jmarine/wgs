package org.wgs.service.game;

import java.io.Serializable;
import java.util.Map;


public interface GroupInternalData extends Serializable
{
    public abstract void init(Map<String,Object> options);
  
}
