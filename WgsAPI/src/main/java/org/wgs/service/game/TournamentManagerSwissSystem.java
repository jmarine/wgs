package org.wgs.service.game;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.wgs.util.Storage;
import org.wgs.wamp.WampSocket;

/**
 *
 * @author jordi
 */
public class TournamentManagerSwissSystem extends TournamentManager 
{
    @Override
    public boolean isDrawAllowed() 
    {
        return true;
    }
    
    @Override
    public TournamentRound createRound(Module wgsModule, WampSocket socket, Collection<Application> apps, Tournament tournament) throws Exception 
    {
        TournamentRound round = persistNextRound(wgsModule, socket, apps, tournament);
       
        Application app = tournament.getApplication();
        
        List<TournamentEnrollment> registrations = tournament.getEnrollments();
        int minByes = registrations.stream().reduce((x, y) -> ((x.getByesCount() <= y.getByesCount())  ? x : y)).get().getByesCount();
                
        
        HashSet<TournamentEnrollment> matched = new HashSet<TournamentEnrollment>();
        
        List<TournamentEnrollment> candidates = new ArrayList<TournamentEnrollment>(registrations);
        candidates.sort((a, b) -> Double.compare(b.getPoints(), a.getPoints()));
        

        // Odd players (BYE process for worst player)
        int offset = 0;
        if (candidates.size() % 2 != 0) {
            for (int i = candidates.size() - 1; i >= 0; i--) {
                TournamentEnrollment registration = candidates.get(i);
                if (registration.getByesCount() == minByes) {
                    matched.add(registration);
                    
                    offset++;
                    TournamentMatch match = createMatch(wgsModule, socket, apps, app, tournament, round, offset, registration, null, "Swiss", app.getWinScoreInTournament());  // BYE WITH SCORE
                    round.getMatches().add(match);
                    
                    break;
                }
            }
        }

        // Generate Matches
        for (int i = 0; i < candidates.size(); i++) {
            TournamentEnrollment r1 = candidates.get(i);
            if (matched.contains(r1)) continue;

            for (int j = i + 1; j < candidates.size(); j++) {
                TournamentEnrollment r2 = candidates.get(j);
                
                // Not matched and no previous round matches.
                if (!matched.contains(r2) && !r1.getPreviousRoundsOpponents().contains(r2.getTeam())) {
                    offset++;
                    TournamentMatch match = createMatch(wgsModule, socket, apps, app, tournament, round, offset, r1, r2, "Swiss", app.getWinScoreInTournament());
                    round.getMatches().add(match);
                    
                    matched.add(r1);
                    matched.add(r2);
                    
                    break;
                }
            }
        }
        
        round = Storage.saveEntity(round);  
        return round;
    }    

    @Override
    public void onGameFinished(Module wgsModule, WampSocket socket, Collection<Application> apps, TournamentMatch match, String gid) throws Exception 
    {
        match = super.closeMatch(apps, match, gid);
        
        
        boolean pendingGamesInRound = false;
        int numTeamsWithSameScore = 0;
        double maxScore = 0;
        
        for(TournamentMatch anyMatch : match.getRound().getMatches()) {
            if(anyMatch.getStatus() != GroupStatus.FINISHED) {
                pendingGamesInRound = true;
            } else {
                for(TournamentMatchParticipantAndResult participantWithResult : anyMatch.getTeamsParticipantsWithResults()) {
                    TournamentEnrollment enroll = participantWithResult.getEnrollment();
                    if(enroll.getPoints() == maxScore) {
                        numTeamsWithSameScore++;
                    } else if(enroll.getPoints() >= maxScore) {
                        maxScore = enroll.getPoints();
                        numTeamsWithSameScore = 1;
                    }
                }
            }
        }
        
        if(!pendingGamesInRound) {
            Tournament tournament = match.getRound().getTournament();
            if(numTeamsWithSameScore > 1) {
                // next round
                createRound(wgsModule, socket, apps, match.getRound().getTournament());
                onTournamentChange(wgsModule, socket, tournament, "update");
            } else {
                // end of tournament
                tournament.setStatus(GroupStatus.FINISHED);
                tournament = Storage.saveEntity(tournament);
                onTournamentChange(wgsModule, socket, tournament, "finished");
            }
        }
    }
    
}
