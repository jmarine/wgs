package org.wgs.service.game;

import java.util.Collection;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.type.WampObject;


public interface GroupActionValidator {

    boolean isValidAction(Module module, WampSocket socket, Collection<Application> apps, Group g, String actionName, String actionValue, int actionSlot) throws Exception;

    WampObject getPrivateState(Group g, Member member);
    
}
