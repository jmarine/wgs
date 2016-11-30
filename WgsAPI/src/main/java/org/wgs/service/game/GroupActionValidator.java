package org.wgs.service.game;

import java.util.Collection;


public interface GroupActionValidator {

    boolean isValidAction(Collection<Application> apps, Group g, String actionName, String actionValue, Long actionSlot) throws Exception;
    
}
