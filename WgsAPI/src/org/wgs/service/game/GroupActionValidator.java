/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.wgs.service.game;

import java.util.Collection;

/**
 *
 * @author jordi
 */
public interface GroupActionValidator {

    boolean isValidAction(Collection<Application> apps, Group g, String actionName, String actionValue, Long actionSlot) throws Exception;
    
}
