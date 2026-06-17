package org.wgs.service.game;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.wgs.util.Storage;
import org.wgs.wamp.WampSocket;

/**
 *
 * @author jordi
 */
public class TournamentManagerKnockoutDoubleElimination extends TournamentManagerKnockoutSingleElimination 
{
    @Override
    public int getLives()
    {
        return 2;
    }
    
    @Override
    public boolean isDrawAllowed() 
    {
        return false;
    }
    
    @Override
    public TournamentRound createRound(Module wgsModule, WampSocket socket, Collection<Application> apps, Tournament tournament) throws Exception 
    {
        TournamentRound round = persistNextRound(wgsModule, socket, apps, tournament);

        List<TournamentEnrollment> registrations0 = tournament.getEnrollments().stream().filter( (enrollment) -> { return (enrollment.getLoses() == 0); }).sorted((a, b) -> Double.compare(b.getPoints(), a.getPoints())).toList();
        List<TournamentEnrollment> registrations1 = tournament.getEnrollments().stream().filter( (enrollment) -> { return (enrollment.getLoses() == 1); }).sorted((a, b) -> Double.compare(b.getPoints(), a.getPoints())).toList();

        int count0 = 0;
        int count1 = 0;
        
        // Winners branch
        if(registrations0.size() >= 2) {
            count0 = createRoundMatchesForFilteredEnrollments(wgsModule, socket, apps, tournament, round, registrations0, "Winners", 0);
        } else if(registrations1.size() >= 2) {
            // Classified for next final round (like a BYE without score)
            TournamentMatch match = createMatch(wgsModule, socket, apps, tournament.getApplication(), tournament, round, 0, registrations0.get(0), null, "Winners", 0.0);
            round.getMatches().add(match);
            count0 = 1;
        }
        
        // Losers branch
        if(registrations1.size() >= 2) {
            count1 = createRoundMatchesForFilteredEnrollments(wgsModule, socket, apps, tournament, round, registrations1, "Losers", count0);
        }      
        
        // Final branch
        if(count0 == 0 && count1 == 0) {
            // match winner branch & loser branch.
            List<TournamentEnrollment> registrationsBothBranches = new ArrayList<TournamentEnrollment>();
            registrationsBothBranches.addAll(registrations0);
            registrationsBothBranches.addAll(registrations1);
            
            createRoundMatchesForFilteredEnrollments(wgsModule, socket, apps, tournament, round, registrationsBothBranches, "Final", 0);        
        }
        
        round = Storage.saveEntity(round);    
        return round;
    }
    
}
