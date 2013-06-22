package org.wgs.core;

import org.wgs.entity.User;
import java.util.Calendar;
import java.util.List;


public class Tournament 
{
    GroupState  state;  // OPEN, STARTED, FINISHED
    Calendar   creation;
    Calendar   start;
    
    int minPlayers;
    int maxPlayers;
    List<User> players;
    User winner;

    int currentStage;
    List<Stage> stages;
    
}