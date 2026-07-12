package org.wgs.service.game;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.script.*;

import org.wgs.security.User;
import org.wgs.util.Storage;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.type.WampObject;


public class WebGL8x8BoardGamesValidator implements GroupActionValidator 
{

    @Override
    public WampObject getPrivateState(Group g, Member member)
    {
        return null;
    }
    
    
        
    @Override
    public boolean isValidAction(Module module, WampSocket socket, Collection<Application> apps, Group g, String actionName, String actionValue, int actionSlot) throws Exception 
    {
        ScriptEngine ruleEngine = null;
        try {
            boolean isValid = false;
            if(actionName.equals("CHAT")) return true;
            // else if(actionSlot < 0) return false;
            
            GroupAction lastAction = null;
            String gameType = g.getApplication().getName();
            gameType = Character.toUpperCase(gameType.charAt(0)) + gameType.substring(1);

            ruleEngine = RuleEngine.getRuleEngine(apps);
            ruleEngine.eval("var game = app.model.GameFactory.createGame('" + gameType+ "');");
            
            if(actionName.equalsIgnoreCase("MOVE") && !g.getData().equals(g.getInitialData())) {
                // Optimization: only evaluate from latest game data
                if(!actionValue.matches("[a-z|0-9]+[R|N|B|Q]?")) throw new Exception("Invalid move syntax:"+actionValue);
                
                ruleEngine.eval("game.initFromStateStr('"+g.getData()+"');");
                int turn = ((Number)ruleEngine.eval("game.getTurn()")).intValue();
                if(actionSlot < 0 || turn != actionSlot+1) {
                    System.err.println("ActionValidator: MOVE action, but incorrect turn.");
                } else {
                    isValid = (boolean)ruleEngine.eval("game.isValidMove('" + actionValue + "')");
                    if(isValid) {
                        String newState = (String)ruleEngine.eval("game.makeMove(game.parseMoveString('" + actionValue + "')); game.toString()");
                        System.out.println("ActionValidator: new state = " + newState);
                        g.setData(newState);
                        
                        boolean isOver = (boolean)ruleEngine.eval("game.isOver()");
                        System.out.println("ActionValidator: ISOVER=" + isOver);                        
                        
                        if(isOver) {
                            
                            int winner = ((Number)ruleEngine.eval("game.getWinner()")).intValue();
                            
                            ruleEngine.eval("game.setWinner('"+winner+"');");
                            String data = (String)ruleEngine.eval("game.toString();");
                            g.setData(data);  
                            g.setWinner(winner);
                            g.setState(GroupState.FINISHED);

                            System.out.println("ActionValidator: WINNER=" + winner);
                            Member m0 = g.getMember(winner-1);
                            Member m1 = g.getMember(2-winner);
                            saveAchievement(g, m0.getRole(), m0.getUser(), "WIN", m1.getUser().getUid());
                            saveAchievement(g, m1.getRole(), m1.getUser(), "LOSE", m0.getUser().getUid());

                            Ranking ranking = Ranking.getInstance(g.getApplication());
                            ranking.addResult(m0.getUser(), m1.getUser(), 1.0); 
                            ranking.updateRatings();
                        }
                        
                    }
                    
                }
                
            } else {
                
                // REPLAY ALL GAME ACTIONS:
                ruleEngine.eval("game.initFromStateStr('"+g.getInitialData()+"');");
                for(GroupAction action : g.getActions()) {
                  if(!action.getActionName().equals("CHAT") && !action.getActionName().equals("CLAIM_VICTORY")) {
                    lastAction = action;

                    if(actionName.equalsIgnoreCase("RETRACT_QUESTION")) {
                        String gameState = (String)ruleEngine.eval("game.toString();");
                        if(gameState.equals(actionValue)) {
                            isValid = true;
                            break;
                        }
                    }

                    switch(action.getActionName()) {
                        case "MOVE":
                            ruleEngine.eval("game.makeMove(game.parseMoveString('" + action.getActionValue() + "'));");
                            break;
                        case "RETRACT_ACCEPTED":
                            ruleEngine.eval("game.initFromStateStr('"+action.getActionValue()+"');");
                            break;
                        default:
                            break;
                    }

                  }
                }            

                if(actionName.equalsIgnoreCase("INIT") || actionName.equalsIgnoreCase("START")) {
                    isValid = true;

                } else if(actionName.equalsIgnoreCase("DRAW_QUESTION")) {
                    isValid = true;    

                } else if(lastAction != null && lastAction.getActionName().equals("DRAW_QUESTION") 
                        && actionSlot >= 0 && actionSlot != lastAction.getSlot()
                        && actionName.equalsIgnoreCase("DRAW_ACCEPTED")) {
                    
                    if(g.getState() == GroupState.STARTED) {
                    
                        int winnerTeam = 0;
                        ruleEngine.eval("game.setWinner('"+winnerTeam+"');");
                        String data = (String)ruleEngine.eval("game.toString();");
                        g.setData(data);                    
                        g.setWinner(winnerTeam);
                        g.setState(GroupState.FINISHED);                

                        Member m0 = g.getMember(0);
                        Member m1 = g.getMember(1);
                        saveAchievement(g, m0.getRole(), m0.getUser(), "DRAW", m1.getUser().getUid());
                        saveAchievement(g, m1.getRole(), m1.getUser(), "DRAW", m0.getUser().getUid());

                        Ranking ranking = Ranking.getInstance(g.getApplication());
                        ranking.addResult(m0.getUser(), m1.getUser(), 0.5); 
                        ranking.updateRatings();
                    }

                    isValid = true;      
                    
                } else if(lastAction != null && lastAction.getActionName().equals("DRAW_QUESTION") 
                        && actionSlot >= 0 && actionSlot != lastAction.getSlot()
                        && actionName.equalsIgnoreCase("DRAW_REJECTED")) {
                    isValid = true;
                    
                } else if(actionName.equalsIgnoreCase("CLAIM_VICTORY")) {

                    if(g.getState() == GroupState.STARTED) {

                        // TODO: check for 3 repeated data states or 50 moves without captures nor pawn moves.

                        int winnerTeam = -1;
                        int maxTurnInMinutes = 5;
                        TournamentMatch tournamentMatch = null;
                        List<TournamentMatch> matches = Storage.findEntities(TournamentMatch.class, "wgs.findTournamentMatchByGID", g.getGid());
                        if(!matches.isEmpty()) {
                            // allow more time at start: tournament games are auto-started, and players may not be present, yet.
                            tournamentMatch = matches.get(0);
                            int gameTimeInMinutes = (int)(Calendar.getInstance().getTimeInMillis() - tournamentMatch.getRound().getStartDate().getTimeInMillis()) / (60 * 1000);
                            int remainingTimeInMinutes = tournamentMatch.getRound().getTournament().getMaxRoundDurationInMinutes() - gameTimeInMinutes;
                            if(remainingTimeInMinutes > gameTimeInMinutes) maxTurnInMinutes = Math.max(maxTurnInMinutes, remainingTimeInMinutes);
                        }

                        Calendar expirationCalendar = Calendar.getInstance();
                        expirationCalendar.add(Calendar.MINUTE, -maxTurnInMinutes);
                        if(lastAction != null && lastAction.getActionTime().before(expirationCalendar) ) {
                            winnerTeam = 1 + g.getTurn();  // next turn team wins
                        } else if(tournamentMatch != null) {
                            // force winner after expected round due date.
                            expirationCalendar = Calendar.getInstance();
                            if((tournamentMatch.getRound().getStartDate().getTimeInMillis() + tournamentMatch.getRound().getTournament().getMaxRoundDurationInMinutes()*60*1000) < expirationCalendar.getTimeInMillis()) {    
                                winnerTeam = 1 + g.getTurn();                                
                            }
                        }

                        if(winnerTeam >= 0) {
                            ruleEngine.eval("game.setWinner('"+winnerTeam+"');");
                            String data = (String)ruleEngine.eval("game.toString();");
                            g.setData(data);                    
                            g.setWinner(winnerTeam);
                            g.setState(GroupState.FINISHED);       
                        }
                    }
                    isValid = true;

                } else if(actionName.equalsIgnoreCase("RESIGN") && actionSlot >= 0) {
                    
                    if(g.getState() == GroupState.STARTED) {
                        int winner = 2 - actionSlot - 1;
                        ruleEngine.eval("game.setWinner('"+winner+"');");
                        String data = (String)ruleEngine.eval("game.toString();");
                        g.setData(data);                    
                        g.setWinner(winner);
                        g.setState(GroupState.FINISHED);

                        if(g.getNumSlots() == 2)  {
                            Member m0 = g.getMember(2-actionSlot-1);
                            Member m1 = g.getMember(actionSlot);
                            if(m0 != null && m0.getUser() != null 
                                    && m1 != null && m1.getUser() != null) {
                                saveAchievement(g, m0.getRole(), m0.getUser(), "WIN", m1.getUser().getUid());
                                saveAchievement(g, m1.getRole(), m1.getUser(), "LOSE", m0.getUser().getUid());

                                Ranking ranking = Ranking.getInstance(g.getApplication());
                                ranking.addResult(m0.getUser(), m1.getUser(), 1.0);                            
                                ranking.updateRatings();
                            }
                        }
                    }
                    isValid = true;

                } else if(lastAction != null && lastAction.getActionName().equals("RETRACT_QUESTION") 
                        && actionSlot >= 0 && actionSlot != lastAction.getSlot()
                        && actionName.equalsIgnoreCase("RETRACT_ACCEPTED")) {

                    String gameState = lastAction.getActionValue();
                    if(gameState.equals(actionValue)) {
                        /*
                        if(g.getState() == GroupState.FINISHED) {  
                            // NOT SUPPORTED (Rollback Ratings, Achievements and TournamentRounds)
                            g.setState(GroupState.STARTED);
                        }
                        */                    

                        if(g.getState() == GroupState.STARTED) {
                            ruleEngine.eval("game.initFromStateStr('"+gameState+"');");
                            g.setData(gameState);
                            isValid = true;
                        }
                    }

                } else if(lastAction != null && lastAction.getActionName().equals("RETRACT_QUESTION") 
                        && actionSlot >= 0 && actionSlot != lastAction.getSlot()
                        && actionName.equalsIgnoreCase("RETRACT_REJECTED")) {

                    String gameState = (String)ruleEngine.eval("game.toString();");
                    if(gameState.equals(actionValue)) {
                        ruleEngine.eval("game.initFromStateStr('"+gameState+"');");
                        isValid = true;
                    }
                    
                } else if(actionName.equalsIgnoreCase("MOVE")) {
                    if(!actionValue.matches("[a-z|0-9]+[R|N|B|Q]?")) throw new Exception("Invalid move syntax:"+actionValue);

                    int turn = ((Number)ruleEngine.eval("game.getTurn()")).intValue();
                    if(actionSlot < 0 || turn != actionSlot+1) {
                        System.err.println("ActionValidator: MOVE action, but incorrect turn.");
                    } else {
                        isValid = (boolean)ruleEngine.eval("game.isValidMove('" + actionValue + "')");
                        if(isValid) {
                            String newState = (String)ruleEngine.eval("game.makeMove(game.parseMoveString('" + actionValue + "')); game.toString()");
                            System.out.println("ActionValidator: new state = " + newState);
                            g.setData(newState);

                            boolean isOver = (boolean)ruleEngine.eval("game.isOver()");
                            System.out.println("ActionValidator: ISOVER=" + isOver);                        

                            if(isOver) {
                                
                                int winner = ((Number)ruleEngine.eval("game.getWinner()")).intValue();
                                ruleEngine.eval("game.setWinner('"+winner+"');");
                                String data = (String)ruleEngine.eval("game.toString();");
                                g.setData(data);  
                                g.setWinner(winner);
                                g.setState(GroupState.FINISHED);                                
                                
                                System.out.println("ActionValidator: WINNER=" + winner);
                                Member m0 = g.getMember(winner-1);
                                Member m1 = g.getMember(2-winner);
                                saveAchievement(g, m0.getRole(), m0.getUser(), "WIN", m1.getUser().getUid());
                                saveAchievement(g, m1.getRole(), m1.getUser(), "LOSE", m0.getUser().getUid());
                                
                                Ranking ranking = Ranking.getInstance(g.getApplication());
                                ranking.addResult(m0.getUser(), m1.getUser(), 1.0);
                                ranking.updateRatings();
                            }

                        }
                    }
                }

            }
            
            System.out.println("ActionValidator: " + actionName + ": valid = " + isValid);
            if(isValid) {
                if(actionName.equals("RETRACT_QUESTION") || actionName.equals("DRAW_QUESTION")) {
                    g.setTurn(1-actionSlot);
                } else {
                    int turn = ((Number)ruleEngine.eval("game.getTurn()")).intValue();
                    g.setTurn(turn-1);
                }
                System.out.println("ActionValidator: " + actionName + ": new turn = " + g.getTurn());
            }
            
            return isValid;
            
        } catch(Exception ex) {
            System.err.println("Error: " + ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            return false;
            
        } finally {
            if(ruleEngine != null) {
                RuleEngine.recycleRuleEngine(ruleEngine);
            }
        }
    }

    
    private void saveAchievement(Group group, Role role, User user, String type, String val)
    {
        Achievement appEvent = new Achievement();
        appEvent.setApp(group.getApplication());
        appEvent.setGid(group.getGid());
        appEvent.setName(type);
        appEvent.setValue(val);
        appEvent.setSourceRole(role);
        appEvent.setSourceUser(user);
        appEvent.setWhen(Calendar.getInstance());
        Storage.saveEntity(appEvent);
    }
    

}
