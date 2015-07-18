package org.wgs.service.game;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.script.*;

import org.wgs.security.User;
import org.wgs.util.Storage;


public class WebGL8x8BoardGamesValidator implements GroupActionValidator 
{
    private static ConcurrentLinkedQueue<ScriptEngine> ruleEngines = new ConcurrentLinkedQueue<>();

        
    @Override
    public boolean isValidAction(Collection<Application> apps, Group g, String actionName, String actionValue, Long actionSlot) throws Exception 
    {
        ScriptEngine ruleEngine = null;
        try {
            boolean isValid = false;
            if(actionName.equals("CHAT")) return true;
            // else if(actionSlot < 0) return false;
            
            GroupAction lastAction = null;
            String gameType = g.getApplication().getName();
            gameType = Character.toUpperCase(gameType.charAt(0)) + gameType.substring(1);

            ruleEngine = getRuleEngine(apps);
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
                            g.setState(GroupState.FINISHED);
                            
                            //g.setWinner(winner);
                            int winner = ((Number)ruleEngine.eval("game.getWinner()")).intValue();
                            System.out.println("ActionValidator: WINNER=" + winner);
                            Member m0 = g.getMember(winner-1);
                            Member m1 = g.getMember(2-winner);
                            saveAchievement(g, m0.getRole(), m0.getUser(), "WIN", m1.getUser().getUid());
                            saveAchievement(g, m1.getRole(), m1.getUser(), "LOSE", m0.getUser().getUid());
                        }
                        
                    }
                    
                }
                
            } else {
                
                // REPLAY ALL GAME ACTIONS:
                ruleEngine.eval("game.initFromStateStr('"+g.getInitialData()+"');");
                for(GroupAction action : g.getActions()) {
                  if(!action.getActionName().equals("CHAT")) {
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

                if(actionName.equalsIgnoreCase("INIT")) {
                    isValid = true;

                } else if(actionName.equalsIgnoreCase("DRAW_QUESTION")) {
                    isValid = true;    

                } else if(lastAction != null && lastAction.getActionName().equals("DRAW_QUESTION") 
                        && actionSlot >= 0 && actionSlot != lastAction.getSlot()
                        && actionName.equalsIgnoreCase("DRAW_ACCEPTED")) {
                    g.setState(GroupState.FINISHED);                

                    //g.setWinner(0);                
                    Member m0 = g.getMember(0);
                    Member m1 = g.getMember(1);
                    saveAchievement(g, m0.getRole(), m0.getUser(), "DRAW", m1.getUser().getUid());
                    saveAchievement(g, m1.getRole(), m1.getUser(), "DRAW", m0.getUser().getUid());

                    isValid = true;                  

                } else if(lastAction != null && lastAction.getActionName().equals("DRAW_QUESTION") 
                        && actionSlot >= 0 && actionSlot != lastAction.getSlot()
                        && actionName.equalsIgnoreCase("DRAW_REJECTED")) {
                    isValid = true;

                } else if(actionName.equalsIgnoreCase("RESIGN") && actionSlot >= 0) {
                    g.setState(GroupState.FINISHED);

                    if(g.getNumSlots() == 2)  {
                        //g.setWinner(actionSlot);
                        Member m0 = g.getMember(2-actionSlot.intValue()-1);
                        Member m1 = g.getMember(actionSlot.intValue());
                        if(m0 != null && m0.getUser() != null 
                                && m1 != null && m1.getUser() != null) {
                            saveAchievement(g, m0.getRole(), m0.getUser(), "WIN", m1.getUser().getUid());
                            saveAchievement(g, m1.getRole(), m1.getUser(), "LOSE", m0.getUser().getUid());
                        }
                    }

                    isValid = true;

                } else if(lastAction != null && lastAction.getActionName().equals("RETRACT_QUESTION") 
                        && actionSlot >= 0 && actionSlot != lastAction.getSlot()
                        && actionName.equalsIgnoreCase("RETRACT_ACCEPTED")) {

                    String gameState = lastAction.getActionValue();
                    if(gameState.equals(actionValue)) {
                        ruleEngine.eval("game.initFromStateStr('"+gameState+"');");
                        g.setData(gameState);
                        if(g.getState() == GroupState.FINISHED) {
                            g.setState(GroupState.STARTED);
                        }
                        isValid = true;
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
                                g.setState(GroupState.FINISHED);

                                //g.setWinner(winner);
                                int winner = ((Number)ruleEngine.eval("game.getWinner()")).intValue();
                                System.out.println("ActionValidator: WINNER=" + winner);
                                Member m0 = g.getMember(winner-1);
                                Member m1 = g.getMember(2-winner);
                                saveAchievement(g, m0.getRole(), m0.getUser(), "WIN", m1.getUser().getUid());
                                saveAchievement(g, m1.getRole(), m1.getUser(), "LOSE", m0.getUser().getUid());
                            }

                        }
                    }
                }

            }
            
            System.out.println("ActionValidator: " + actionName + ": valid = " + isValid);
            if(isValid) {
                if(actionName.equals("RETRACT_QUESTION") || actionName.equals("DRAW_QUESTION")) {
                    g.setTurn(1-actionSlot.intValue());
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
                recycleRuleEngine(ruleEngine);
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
    
    
    private ScriptEngine getRuleEngine(Collection<Application> apps) throws Exception
    {
        ScriptEngine ruleEngine = ruleEngines.poll();
        if(ruleEngine == null) {
            ScriptEngineManager factory = new ScriptEngineManager();
            ruleEngine = factory.getEngineByName("JavaScript");

            ClassLoader cl = this.getClass().getClassLoader();
            ruleEngine.eval(new InputStreamReader(cl.getResourceAsStream("META-INF/rules/move.js"),StandardCharsets.UTF_8));
            ruleEngine.eval(new InputStreamReader(cl.getResourceAsStream("META-INF/rules/game.js"),StandardCharsets.UTF_8));

            ArrayList<Application> appsToLoad = new ArrayList<Application>();
            appsToLoad.addAll(apps);                
            while(!appsToLoad.isEmpty()) {  // Note: inherited classes may fail until super class has been loaded
                Iterator<Application> iter = appsToLoad.iterator();
                while(iter.hasNext()) {
                    try { 
                        Application app = iter.next();
                        ruleEngine.eval(new InputStreamReader(cl.getResourceAsStream("META-INF/rules/" + app.getName() +".js"),StandardCharsets.UTF_8)); 
                        iter.remove();
                    } catch(Exception ex) { }
                }
            }        
        }

        return ruleEngine;
    }    

    
    private void recycleRuleEngine(ScriptEngine ruleEngine)
    {
        if(ruleEngine != null) ruleEngines.offer(ruleEngine);
    }

}
