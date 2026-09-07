package org.wgs.service.game;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import javax.script.ScriptEngine;
import org.wgs.security.User;
import org.wgs.util.Storage;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.type.WampDict;

/**
 *
 * @author jordi
 */
public abstract class TournamentManager 
{
    public abstract boolean isDrawAllowed();
    
    public abstract TournamentRound createRound(Module wgsModule, WampSocket socket, Collection<Application> apps, Tournament tournament) throws Exception;
    
    public abstract void onGameFinished(Module wgsModule, WampSocket socket, Collection<Application> apps, TournamentMatch match, String gid) throws Exception;
   
    public void onTournamentChange(Module wgsModule, WampSocket socket, Tournament tournament, String cmd) throws Exception
    {
        WampDict event = tournament.toWampObject();
        event.put("cmd", cmd);
        
        boolean excludeMe = true;
        socket.publishEvent(WampBroker.getTopic(wgsModule.getFQtopicURI("tournament_event")), null, event, excludeMe, false);        
    }
    

    protected TournamentRound persistNextRound(Module wgsModule, WampSocket socket, Collection<Application> apps, Tournament tournament) throws Exception
    {
        int newRoundCount = 1+tournament.getCurrentRound();
        
        Calendar startTime = Calendar.getInstance();
        if(newRoundCount == 1 && startTime.after(tournament.getStart())) {
            startTime = tournament.getStart();
        }
        
        TournamentRound round = new TournamentRound();
        round.setTournament(tournament);
        round.setCurrentRound(newRoundCount);
        round.setStartDate(startTime);
        round = Storage.saveEntity(round);

        tournament.setCurrentRound(newRoundCount);
        tournament.getRounds().add(round);
        tournament = Storage.saveEntity(tournament);        
        
        return round;
    }

    
    protected boolean isMatchComplete(TournamentMatch match)
    {
        Application app  = match.getRound().getTournament().getApplication();
        
        for(TournamentMatchParticipantAndResult p1 : match.getTeamsParticipantsWithResults()) {
            if( isDrawAllowed()
                    || (app.getMinWinGamesToScoreOrKnockout() > 0 && p1.getWins() >= app.getMinWinGamesToScoreOrKnockout())
                    || (app.getMaxWinGamesToScoreOrKnockout() > 0 && p1.getWins() >= app.getMaxWinGamesToScoreOrKnockout()) ) {
                return true;
            }
            
            for(TournamentMatchParticipantAndResult p2 : match.getTeamsParticipantsWithResults()) {
                int wins1 = p1.getWins();
                int wins2 = p2.getWins();
                if(app.getDiffWinsToScoreOrKnockout() > 0 && Math.abs(wins1 - wins2) >= app.getDiffWinsToScoreOrKnockout()) {
                    return true;
                } 
            }
        }
        return false;
    }
    

    protected Group createGroup(Module wgsModule, WampSocket socket, Collection<Application> apps, Application app, Tournament tournament, TournamentRound round, int positionOrderInRound, TournamentEnrollment enroll1, TournamentEnrollment enroll2, String branch) throws Exception
    {
        List<User> members1 = enroll1.getTeam().getMembers();
        List<User> members2 = enroll2.getTeam().getMembers();
        
        Group group = new Group();
        group.setGid(UUID.randomUUID().toString());
        group.setApplication(app);
        group.setAutoMatchEnabled(false);
        group.setAdmin(tournament.getOwner());
        group.setAlliancesAllowed(app.isAlliancesAllowed());
        group.setDeltaMembers(app.getDeltaMembers());
        group.setDynamicGroup(app.isDynamicGroup());
        group.setDescription(tournament.getName() + " - R" + round.getCurrentRound() + " - G" + positionOrderInRound + ((branch != null) ? " - " + branch : "") );
        group.setMinMembers(tournament.getMinTeams() * app.getMinPlayersByTeam());
        group.setMaxMembers(members1.size() + members2.size());
        group.setStatus(GroupStatus.STARTED);
        createGameDataAndTurn(apps, app, group);
        Storage.createEntity(group);

        
        if(enroll1.getPreviousColorCount(0) > enroll2.getPreviousColorCount(0)) {
            TournamentEnrollment tmp = enroll1;
            enroll1 = enroll2;
            enroll2 = tmp;
        }
        
        ArrayList<User> players = new ArrayList<User>();
        players.addAll(members1);
        players.addAll(members2);
        if(!app.isTeamPlayersInOrder()) {
            // swap odd players 
            List<User> m1 = enroll1.getTeam().getMembers();
            for(int i = 1; i < m1.size(); i = i + 2) {
                User tmp = players.get(i);
                players.set(i, players.get(i+m1.size()-1));
                players.set(i+m1.size()-1, tmp);
            }
        }

        int slot = 0;
        for(User player : players) {
            Role role = null;
            if(slot < app.getRoles().size()) role = app.getRoles().get(slot);

            int team = 0;
            if(app.isTeamPlayersInOrder()) {
                team = (slot < members1.size()) ? 1 : 2;
            } else {
                team = ((slot % 2) == 0) ? 1 : 2;
            }
            
            Member member = new Member();
            member.setApplicationGroup(group);
            member.setSlot(slot);
            member.setRole(role);
            member.setUser(player);
            member.setTeam(team);
            Storage.createEntity(member);
            group.setMember(slot, member);
            slot++;
        }
        
        group = Storage.saveEntity(group);
        
        GroupActionValidator validator = null;
        String validatorClassName = group.getApplication().getActionValidator();
        if(validatorClassName != null) validator = (GroupActionValidator)Class.forName(validatorClassName).getDeclaredConstructor().newInstance();

        if(validator == null || validator.isValidAction(wgsModule, socket, wgsModule.getApplications(), group, "INIT", group.getInitialData(), -1)) {
            // manager.merge(g);
            app.addGroup(group);
            if(validator != null) {
                int actionSlot = Math.max(0, group.getTurn());
                boolean isValid = validator.isValidAction(wgsModule, socket, wgsModule.getApplications(), group, "START", tournament.getOwner().getUid(), actionSlot);
                if(isValid) {
                    group = Storage.saveEntity(group);
                }
            }
        }
        
        wgsModule.broadcastAppEventInfo(socket, group, "group_updated");

        return group;
    }
    
    protected TournamentMatch createMatch(Module wgsModule, WampSocket socket, Collection<Application> apps, Application app, Tournament tournament, TournamentRound round, int positionOrderInRound, TournamentEnrollment enroll1, TournamentEnrollment enroll2, String branch, double byePoints) throws Exception
    {
        Group group = null;

        TournamentMatch match = new TournamentMatch();
        match.setRound(round);
        match.setPositionOrderInRound(positionOrderInRound);
        if(enroll1 != null && enroll2 != null) {
            group = createGroup(wgsModule, socket, apps, app, tournament, round, positionOrderInRound, enroll1, enroll2, branch);
            match.setStatus(GroupStatus.STARTED);
            match.setBye(false);
        } else {
            match.setStatus(GroupStatus.FINISHED);
            match.setBye(true);
        }
        
        Storage.createEntity(match);
        
        
        TournamentMatchParticipantAndResult p1 = new TournamentMatchParticipantAndResult();
        p1.setEnrollment(enroll1);
        p1.setMatch(match);
        p1.setPositionOfTeams(0);
        if(enroll2 == null) {
            p1.setPoints(byePoints);
            p1.setResult("BYE");
        }
        
        p1 = Storage.saveEntity(p1);
        match.getTeamsParticipantsWithResults().add(p1);
        
        if(enroll2 == null) {
            if(byePoints > 0) {
                enroll1.setByesCount(1+enroll1.getByesCount());
                enroll1.setPoints(enroll1.getPoints() + byePoints);
                enroll1 = Storage.saveEntity(enroll1);
            }
        } else  {
            TournamentMatchParticipantAndResult p2 = new TournamentMatchParticipantAndResult();
            p2.setEnrollment(enroll2);
            p2.setMatch(match);
            p2.setPositionOfTeams(1);
            p2 = Storage.saveEntity(p2);        
            match.getTeamsParticipantsWithResults().add(p2);
        }
        
        
        if(group != null) {
            match.getGIDs().add(group.getGid());
        }
        
        match = Storage.saveEntity(match);
      
        return match;
    }
    
    

    
    
    private void createGameDataAndTurn(Collection<Application> apps, Application app, Group group) throws Exception
    {
        String gameType = app.getName();
        gameType = Character.toUpperCase(gameType.charAt(0)) + gameType.substring(1);
        
        ScriptEngine ruleEngine = RuleEngine.getRuleEngine(apps);
        try {
            ruleEngine.eval("var game = app.model.GameFactory.createGame('" + gameType+ "');");
            ruleEngine.eval("game.newGame();");
            String data = (String)ruleEngine.eval("game.toString();");
            
            int turn = ((Number)ruleEngine.eval("game.getTurn()")).intValue();
            
            group.setInitialData(data);
            group.setData(data);
            group.setTurn(turn-1);
            
            String internalDataClass = app.getInternalDataClass();
            if(internalDataClass != null) {

                GroupInternalData internalDataObject = null;

                Class clazz = Class.forName(internalDataClass);
                internalDataObject = (GroupInternalData)clazz.getDeclaredConstructor().newInstance();

                if(internalDataObject != null) {
                    internalDataObject.init(app.getInternalDataOptions());
                }

                group.setInternalDataObject(internalDataObject);
            }        
            
        } catch(Throwable ex) {
            
            System.err.println("TournamentManager.createGameDataAndTurn: ERROR: " + ex.getMessage());
            ex.printStackTrace();
            
        } finally {
            if(ruleEngine != null) {
                RuleEngine.recycleRuleEngine(ruleEngine);
            }
        }
        
    }
    
    
    protected TournamentMatch closeMatch(Collection<Application> apps, TournamentMatch match, String gid) throws Exception
    {
        scoreMatch(apps, match, gid); 
        match.setStatus(GroupStatus.FINISHED);
        match = Storage.saveEntity(match);
        
        if(match.getTeamsParticipantsWithResults() != null) {
            for(TournamentMatchParticipantAndResult p1 : match.getTeamsParticipantsWithResults()) {
                TournamentEnrollment enroll1 = p1.getEnrollment();
                for(TournamentMatchParticipantAndResult p2 : match.getTeamsParticipantsWithResults()) {
                    TournamentEnrollment enroll2 = p2.getEnrollment();
                    if(enroll1.getId() != enroll2.getId()) {
                        if(!enroll1.getPreviousRoundsOpponents().contains(enroll2.getTeam())) {
                            enroll1.getPreviousRoundsOpponents().add(enroll2.getTeam());
                            enroll1 = Storage.saveEntity(enroll1);
                        }
                    }
                }
            }       
        }
        
        return match;
    }
    
    private void scoreMatch(Collection<Application> apps, TournamentMatch match, String gid) throws Exception
    {
        Group g = Storage.findEntity(Group.class, gid);
        
        String gameType = g.getApplication().getName();
        gameType = Character.toUpperCase(gameType.charAt(0)) + gameType.substring(1);

        ScriptEngine ruleEngine = RuleEngine.getRuleEngine(apps);
        try {
            ruleEngine.eval("var game = app.model.GameFactory.createGame('" + gameType+ "');");    
            ruleEngine.eval("game.initFromStateStr('"+g.getData()+"');"); 

            int winner = ((Number)ruleEngine.eval("game.getWinner()")).intValue();  // TODO: support array of winner teams?


            Tournament tournament = match.getRound().getTournament();
            Application app = tournament.getApplication();


            switch(winner)
            {
                case 0:  // DRAW
                    for(TournamentMatchParticipantAndResult p : match.getTeamsParticipantsWithResults()) {

                        p.setDraws(p.getDraws() + 1);
                        p =  Storage.saveEntity(p);
                        
                        if(isMatchComplete(match)) {
                            p.setResult("DRAW");
                            p.setPoints(app.getTieScoreInTournament());                        
                            p =  Storage.saveEntity(p);

                            TournamentEnrollment enroll  = p.getEnrollment();
                            enroll.setPoints(enroll.getPoints() + app.getTieScoreInTournament());
                            enroll = Storage.saveEntity(enroll);
                            
                        }
                        
                    }
                    break;
                default:
                    int otherMaxWins = 0;
                    int index = 1;
                    for(TournamentMatchParticipantAndResult p : match.getTeamsParticipantsWithResults()) {

                        if(index++ == winner) {

                            p.setWins(p.getWins() + 1);
                            p = Storage.saveEntity(p);
                            
                        } else {
                            
                            otherMaxWins = Math.max(otherMaxWins, p.getWins());
                            
                            p.setLoses(p.getLoses() + 1);
                            p = Storage.saveEntity(p);  

                        }
                        
                    }
                    
                    TournamentMatchParticipantAndResult winnerParticipant = match.getTeamsParticipantsWithResults().get(winner-1);
                    if( ( (app.getMinWinGamesToScoreOrKnockout() > 0) && (winnerParticipant.getWins() >= app.getMinWinGamesToScoreOrKnockout()) ) 
                            || ( (app.getMaxWinGamesToScoreOrKnockout() > 0) && (winnerParticipant.getWins() >= app.getMaxWinGamesToScoreOrKnockout()) ) 
                            || ( (app.getDiffWinsToScoreOrKnockout() > 0) && ((winnerParticipant.getWins() - otherMaxWins) >= app.getDiffWinsToScoreOrKnockout()) ) ) { 

                        index = 1;
                        for(TournamentMatchParticipantAndResult p : match.getTeamsParticipantsWithResults()) {
                            TournamentEnrollment enroll  = p.getEnrollment();
                            if(index++ == winner) {
                                p.setResult("WIN");
                                p.setPoints(app.getWinScoreInTournament());
                                p = Storage.saveEntity(p);                                
                                
                                enroll.setPoints(enroll.getPoints() + app.getWinScoreInTournament());
                                enroll = Storage.saveEntity(enroll);
                                
                            } else {
                                p.setResult("LOSE");
                                p.setPoints(0.0);
                                p = Storage.saveEntity(p);
                                
                                enroll.setLoses(enroll.getLoses()+1);
                                enroll = Storage.saveEntity(enroll);
                            }
                        }
                    }
                    break;
            }
            
        } finally {
            
            if(ruleEngine != null) {
                RuleEngine.recycleRuleEngine(ruleEngine);
            }
        }
        
        
    }
    
}
