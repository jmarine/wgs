package org.wgs.service.game;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import javax.script.*;


public class TurnValidator implements GroupActionValidator 
{
    @Override
    public boolean validAction(Collection<Application> apps, Group g, String actionName, String actionValue, Long actionSlot) throws Exception 
    {
        try {
            boolean isValid = false;
            if(actionName.equals("CHAT")) return true;
            // else if(actionSlot < 0) return false;
            
            GroupAction lastAction = null;
            String gameType = g.getApplication().getName();
            gameType = Character.toUpperCase(gameType.charAt(0)) + gameType.substring(1);

            ScriptEngineManager factory = new ScriptEngineManager();
            ScriptEngine engine = factory.getEngineByName("JavaScript");

            ClassLoader cl = this.getClass().getClassLoader();
            engine.eval(new InputStreamReader(cl.getResourceAsStream("META-INF/rules/move.js")));
            engine.eval(new InputStreamReader(cl.getResourceAsStream("META-INF/rules/game.js")));

            ArrayList<Application> appsToLoad = new ArrayList<Application>();
            appsToLoad.addAll(apps);                
            while(!appsToLoad.isEmpty()) {  // Note: inherited classes may fail until super class has been loaded
                Iterator<Application> iter = appsToLoad.iterator();
                while(iter.hasNext()) {
                    try { 
                        Application app = iter.next();
                        engine.eval(new InputStreamReader(cl.getResourceAsStream("META-INF/rules/" + app.getName() +".js"))); 
                        iter.remove();
                    } catch(Exception ex) { }
                }
            }

            engine.eval("var game = new "+gameType+"();");
            engine.eval("game.initFromStateStr('"+g.getData()+"');");
            for(GroupAction action : g.getActions()) {
              if(!action.getActionName().equals("CHAT")) {
                lastAction = action;
                
                if(actionName.equalsIgnoreCase("RETRACT_QUESTION")) {
                    String gameState = (String)engine.eval("game.toString();");
                    if(gameState.equals(actionValue)) {
                        isValid = true;
                        break;
                    }
                }
                
                switch(action.getActionName()) {
                    case "MOVE":
                        engine.eval("game.makeMove(game.parseMoveString('" + action.getActionValue() + "'));");
                        break;
                    case "RETRACT_ACCEPTED":
                        engine.eval("game.initFromStateStr('"+action.getActionValue()+"');");
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
                isValid = true;                  

            } else if(lastAction != null && lastAction.getActionName().equals("DRAW_QUESTION") 
                    && actionSlot >= 0 && actionSlot != lastAction.getSlot()
                    && actionName.equalsIgnoreCase("DRAW_REJECTED")) {
                isValid = true;
                
            } else if(actionName.equalsIgnoreCase("RESIGN") && actionSlot >= 0) {
                g.setState(GroupState.FINISHED);
                //g.setWinner(2-actionSlot);
                isValid = true;
                
            } else if(actionName.equalsIgnoreCase("MOVE")) {
                if(!actionValue.matches("[a-z|0-9]+[R|N|B|Q]?")) throw new Exception("Invalid move syntax:"+actionValue);
                
                int turn = ((Number)engine.eval("game.getTurn()")).intValue();
                if(actionSlot < 0 || turn != actionSlot+1) {
                    System.err.println("ActionValidator: MOVE action, but incorrect turn.");
                } else {
                    isValid = (boolean)engine.eval("game.isValidMove('" + actionValue + "')");
                    if(isValid) {
                        String newState = (String)engine.eval("game.makeMove(game.parseMoveString('" + actionValue + "')); game.toString()");
                        System.out.println("ActionValidator: new state = " + newState);
                        //g.setData(newState);  // WARN: group's data contains initial state for chess960
                    }
                    
                }
                
            } else if(lastAction != null && lastAction.getActionName().equals("RETRACT_QUESTION") 
                    && actionSlot >= 0 && actionSlot != lastAction.getSlot()
                    && actionName.equalsIgnoreCase("RETRACT_ACCEPTED")) {
                
                String gameState = lastAction.getActionValue();
                if(gameState.equals(actionValue)) {
                    engine.eval("game.initFromStateStr('"+gameState+"');");
                    if(g.getState() == GroupState.FINISHED) {
                        g.setState(GroupState.STARTED);
                    }
                    isValid = true;
                }
               
            } else if(lastAction != null && lastAction.getActionName().equals("RETRACT_QUESTION") 
                    && actionSlot >= 0 && actionSlot != lastAction.getSlot()
                    && actionName.equalsIgnoreCase("RETRACT_REJECTED")) {
                
                String gameState = (String)engine.eval("game.toString();");
                if(gameState.equals(actionValue)) {
                    engine.eval("game.initFromStateStr('"+gameState+"');");
                    isValid = true;
                }
            }
            
            
            System.out.println("ActionValidator: " + actionName + ": valid = " + isValid);
            if(isValid) {
                if(actionName.equals("RETRACT_QUESTION") || actionName.equals("DRAW_QUESTION")) {
                    g.setTurn(1-actionSlot.intValue());
                } else {
                    int turn = ((Number)engine.eval("game.getTurn()")).intValue();
                    g.setTurn(turn-1);
                }
                System.out.println("ActionValidator: " + actionName + ": new turn = " + g.getTurn());
            }
            
            return isValid;
            
        } catch(Exception ex) {
            System.err.println("Error: " + ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }
}
