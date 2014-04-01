package org.wgs.service.game;

import org.wgs.security.User;
import java.util.Calendar;
import java.util.List;
import java.util.Map;


public class Stage {
    
    int stageType;  // KNOCKOUT = 1, BEST = 2
    int numSets;
    int numPlayersBySetForNextStage;
    int currentRound;
    int minRoundsInSet;
    int minWinDiffToKnockout;
    
    Calendar start;
    
    List<User> stagePlayers[];
    Map<User,UserStat> stats;
    
    List<User> winners[];

    // List<gameGroup> gameGroups;

}


class UserStat 
{
    int  wins;
    int  losses;
    int  stalled;
}
        