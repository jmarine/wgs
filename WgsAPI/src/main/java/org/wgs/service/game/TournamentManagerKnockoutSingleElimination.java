package org.wgs.service.game;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.wgs.util.Storage;
import org.wgs.wamp.WampSocket;

/**
 *
 * @author jordi
 */
public class TournamentManagerKnockoutSingleElimination extends TournamentManager
{
    
    public int getLives()
    {
        return 1;
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
        createRoundMatchesForFilteredEnrollments(wgsModule, socket, apps, tournament, round, registrations0, "Winner", 0);
        
        round = Storage.saveEntity(round);   
        return round;
    }
    
    
    protected int createRoundMatchesForFilteredEnrollments(Module wgsModule, WampSocket socket, Collection<Application> apps, Tournament tournament, TournamentRound round, List<TournamentEnrollment> registrations, String branch, int offset) throws Exception
    {
        Application app = tournament.getApplication();

        int numMatchesWithoutByes = 0;
        int minByes = registrations.stream().reduce((x, y) -> ((x.getByesCount() <= y.getByesCount())  ? x : y)).get().getByesCount();

        List<TournamentEnrollment> candidates = new ArrayList<TournamentEnrollment>(registrations);
        
        // Odd players (BYE process for best players, but no repeat byes)
        if (candidates.size() > 2 && candidates.size() % 2 != 0) {
            for (int i = 0; i < candidates.size(); i++) {
                TournamentEnrollment registration = candidates.get(i);
                if (registration.getByesCount() == minByes) {
                    candidates.remove(registration);
                    
                    offset++;
                    TournamentMatch match = createMatch(wgsModule, socket, apps, app, tournament, round, offset, registration, null, branch, app.getWinScoreInTournament());  // BYE WITH SCORE
                    round.getMatches().add(match);

                    break;
                }
            }
        }

        // Generate Matches
        for (int i = 0; i < candidates.size() / 2; i++) {
            TournamentEnrollment r1 = candidates.get(i);
            TournamentEnrollment r2 = candidates.get(candidates.size()-1-i);

            offset++;
            TournamentMatch match = createMatch(wgsModule, socket, apps, app, tournament, round, offset, r1, r2, branch, app.getWinScoreInTournament());
            round.getMatches().add(match);
            numMatchesWithoutByes++;
        }         
        
        return numMatchesWithoutByes;
    }

    
    @Override
    public void onGameFinished(Module wgsModule, WampSocket socket, Collection<Application> apps, TournamentMatch match, String gid) throws Exception 
    {
        match = super.closeMatch(apps, match, gid);
        
        if(!isMatchComplete(match)) {
            // rematch
            
            List<TournamentMatchParticipantAndResult> participants = match.getTeamsParticipantsWithResults();
            TournamentEnrollment enroll1 = participants.get(0).getEnrollment();
            TournamentEnrollment enroll2 = participants.get(1).getEnrollment();
            
            Group group = createGroup(wgsModule, socket, apps, match.getRound().getTournament().getApplication(), match.getRound().getTournament(), match.getRound(), match.getPositionOrderInRound(), enroll1, enroll2, "Tie Break " + match.getGIDs().size());
            match.getGIDs().add(group.getGid());
            match.setStatus(GroupStatus.STARTED);
            match = Storage.saveEntity(match);
            
        } else {
        
            boolean pendingGamesInRound = false;
            int numPlayersWithLives = 0;

            for(TournamentMatch anyMatch : match.getRound().getMatches()) {
                if(anyMatch.getStatus() != GroupStatus.FINISHED) {
                    pendingGamesInRound = true;
                } else {
                    for(TournamentMatchParticipantAndResult participantWithResult : anyMatch.getTeamsParticipantsWithResults()) {
                        TournamentEnrollment enroll = participantWithResult.getEnrollment();
                        if(enroll.getLoses() < getLives()) {
                            numPlayersWithLives++;
                        }
                    }
                }
            }

            if(!pendingGamesInRound) {
                Tournament tournament = match.getRound().getTournament();
                if(numPlayersWithLives > 1) {
                    // next round
                    createRound(wgsModule, socket, apps, match.getRound().getTournament());
                    onTournamentChange(wgsModule, socket, tournament, "updated");
                } else {
                    // end of tournament
                    tournament.setStatus(GroupStatus.FINISHED);
                    tournament = Storage.saveEntity(tournament);
                    onTournamentChange(wgsModule, socket, tournament, "finished");
                }
            }        
        }
        
    }
    
}
