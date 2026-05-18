package org.wgs.service.game;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.script.*;
import org.wgs.security.User;
import org.wgs.util.Storage;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.encoding.WampSerializer;

import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.topic.WampPublishOptions;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampObject;


public class BotifarraCardGamesValidator implements GroupActionValidator 
{
    private static ConcurrentLinkedQueue<ScriptEngine> ruleEngines = new ConcurrentLinkedQueue<>();
    
    
    @Override
    public WampObject getPrivateState(Group g, Member member)
    {
        WampList cards = new WampList();
        GroupInternalDataForCardGame internalData = (GroupInternalDataForCardGame)g.getInternalDataObject();

        LinkedList<Card> privateCards = internalData.getPrivateCards().get(member.getSlot());
        if(privateCards != null) {
            sortCards(privateCards);
            for(Card card : privateCards) {
                WampDict cardDict = new WampDict();
                cardDict.put("type", card.getType());
                cardDict.put("value", card.getValue());
                cards.add(cardDict);
            }
        }
        
        return cards;
    }
    
    private void sortCards(LinkedList<Card> cards)
    {
        Comparator<Card> cardComparator = new Comparator<Card>() {
            @Override
            public int compare(Card c1, Card c2) {
                return cardOrder(c1) - cardOrder(c2);
            }
            
            private int cardOrder(Card card) {
                int order = getCardPriority(card);
                switch(card.getType()) {
                    case "C":
                        order += 40000;
                        break;
                    case "O":
                        order += 30000;
                        break;
                    case "E":
                        order += 20000;
                        break;
                    case "B":
                        order += 10000;
                        break;
                }
                return -order;
            }
        };
        
        java.util.Collections.sort(cards, cardComparator);
    }
    
        
    
    @Override
    public boolean isValidAction(Module module, WampSocket socket, Collection<Application> apps, Group g, String actionName, String actionValue, int actionSlot) throws Exception 
    {
        boolean isValid = false;     
        boolean notifyPrivateData = false;
        int turn = 0;
        
        ScriptEngine ruleEngine = null;
        try {      

            if(actionName.equals("CHAT")) return true;
            
            String gameType = g.getApplication().getName();
            gameType = Character.toUpperCase(gameType.charAt(0)) + gameType.substring(1);

            ruleEngine = getRuleEngine(apps);
            ruleEngine.eval("var game = app.model.GameFactory.createGame('" + gameType+ "');");    
            if(actionName.equals("INIT") && (g.getInitialData() == null || g.getData().equals(g.getInitialData()))) {
                isValid = true;
            } else {
                ruleEngine.eval("game.initFromStateStr('"+g.getData()+"');");
                WampObject privateCards = getPrivateState(g, g.getMember(actionSlot));
                WampSerializer serializer = WampEncoding.JSON.getSerializer();
                String privateCardsJSON = serializer.serialize(privateCards).toString();
                
                isValid = (boolean)ruleEngine.eval("game.isValidAction(" + actionSlot + ",'" + actionName + "','" + actionValue + "', " + privateCardsJSON + ")");
            }


            if(isValid) {            
                GroupInternalDataForCardGame internalData = (GroupInternalDataForCardGame)g.getInternalDataObject();

                switch(actionName) {
                case "INIT": {                
                        turn = -1;
                        g.setTurn(turn);
                        g.setData(getPublicData(g, internalData));
                        isValid = true;
                    }
                    break;
                    
                case "START": {
                        // al començar la partida, un jugador aleatori repartirà les cartes
                        Dice dice = new Dice(4);
                        turn = dice.getValue() - 1;   
                        
                        int startTurn = -1;
                        while(!internalData.getAvailableCards().isEmpty()) {
                            turn = (turn + 1) % 4;  // repartir al següent jugador                            
                            List<Card> distributed = internalData.distributeCardsToPlayer(turn, 4);
                            for(Card card : distributed) {
                                if(card.getType().equals("E") && card.getValue().equals("5")) {
                                    startTurn = turn;  // el jugador que tingui el 5 d'espases cantarà el trumfo
                                }
                            }
                        }
                        g.setInternalDataObject(internalData);


                        turn = startTurn;
                        
                        g.setTurn(startTurn);                        
                        g.getFlow().put("startTurn", String.valueOf(turn));
                        g.getFlow().put("step", "trump");
                        g.getFlow().put("trump", "");
                        g.setFlow(g.getFlow());
                        
                        g.setData(getPublicData(g, internalData));
                        g.setState(GroupState.STARTED);
                        
                        isValid = true;
                        notifyPrivateData = true;
                    }
                    break;
                    
                case "SET_TRUMP": {
                        if(actionSlot == g.getTurn()) {
                            isValid = true;
                    
                            if(actionValue.equals("DELEGATE")) {
                                int t = g.getTurn();
                                t = (t + 2) % 4;
                                g.setTurn(t);
                            } else {
                                g.setTurn((g.getTurn()+1) % 4);  // PERMETRE CONTRAR
                                g.getFlow().put("step", "double_1");
                                g.getFlow().put("multiplier", "1");                            
                                g.getFlow().remove("ask_pair_to_double");  // to avoid question loops
                            }

                            g.getFlow().put("trump", actionValue);
                            g.setFlow(g.getFlow());
                            
                            g.setData(getPublicData(g, internalData));

                        }                        
                    }
                    break;
                    
                case "REJECT_DOUBLE": {
                        if(actionSlot == g.getTurn()
                                || actionSlot == ((g.getTurn() + 2) % 4) ) {
                            isValid = true;
                    
                            String preguntarParella = (String)g.getFlow().get("ask_pair_to_double");
                            if(preguntarParella == null) {
                                g.getFlow().put("ask_pair_to_double", "yes");
                                g.setTurn((actionSlot + 2) % 4);  // AVISAR PARELLA SI VOL CONTRAR
                            } else {
                                g.setTurn((1+Integer.parseInt(g.getFlow().get("startTurn").toString())) % 4); // TORNAR AL SEGÜENT JUGADOR DE LA DRETA DEL QUE HA COMENÇAT LA PARTIDA INDICANT TRUMFO O QUE HA DELEGAT AL COMPANY
                                g.getFlow().remove("ask_pair_to_double");
                                g.getFlow().put("step", "play");
                            }
                            g.setFlow(g.getFlow());
                            
                            g.setData(getPublicData(g, internalData));

                        }
                    }
                    break;

                case "ACCEPT_DOUBLE": {
                        if(actionSlot == g.getTurn() || actionSlot == ((g.getTurn() + 2) % 4) ) {
                            isValid = true;
                    
                            String multiplier = g.getFlow().getOrDefault("multiplier", "1").toString();
                            if(!multiplier.equals("16")) multiplier = String.valueOf(2 * Integer.parseInt(multiplier));
                            g.getFlow().put("multiplier", multiplier);

                            g.getFlow().remove("ask_pair_to_double");
                            if( (!multiplier.equals("16")) && !(multiplier.equals("4") && g.getFlow().get("trump").equals("BOTIFARRA")) ) {
                                // Allow opponent to duplicate multiplier again
                                g.setTurn((g.getTurn()+1) % 4);  
                                g.getFlow().put("step", "double_" + multiplier);
                            } else {
                                // Start game
                                g.setTurn((1+Integer.parseInt(g.getFlow().get("startTurn").toString())) % 4); // TORNAR AL SEGÜENT JUGADOR DE LA DRETA DEL QUE HA COMENÇAT LA PARTIDA INDICANT TRUMFO O QUE HA DELEGAT AL COMPANY
                                g.getFlow().put("step", "play");
                            }
                            g.setFlow(g.getFlow());
                            
                            g.setData(getPublicData(g, internalData));

                        }
                    }
                    break;
                    
                case "EXPOSE": {
                        Card card = Card.parseCard(actionValue);
                        isValid = internalData.disposeCardFromPlayer(actionSlot, card);
                        
                        if(isValid) {
                            if(internalData.getCurrentRound().getExposedCards().size() == 4) {
                                g.getFlow().put("lastRound", getRoundData(internalData.getCurrentRound()));
                                internalData.newCardRound();
                            }
                            
                            
                            CardRound currentRound = internalData.getCurrentRound();
                            currentRound.addExposedCardsOfPlayer(actionSlot, Arrays.asList(card));
                            g.getFlow().put("currentRound", getRoundData(currentRound));
                            
                            if(currentRound.getExposedCards().size() < 4) {
                                
                                turn = (g.getTurn() + 1) % 4;
                                g.setTurn(turn);
                                
                            } else {
                                
                                int roundWinner = getRoundWinner(g, currentRound);
                                currentRound.setWinner(roundWinner);
                                currentRound.setFlowData(filterFlowDataForCardRound(g.getFlow()));
                                internalData.getRounds().add(currentRound);
                                

                                // add score to player & game flow                                    
                                int roundScore = getRoundScore(currentRound);
                                currentRound.setScore(roundScore);
                                    
                                
                                if(!internalData.getPrivateCards().get(roundWinner).isEmpty()) {
                                    
                                    g.setTurn(roundWinner);
                                    
                                } else {

                                    int pair = 0;
                                    int totalScoreByPair[] = { 0, 0 };
                                    List<CardRound> rounds = internalData.getRounds();
                                    for(CardRound round : rounds) {
                                        pair = round.getWinner() % 2;
                                        totalScoreByPair[pair] += round.getScore();
                                    }

                                    int winnerTeam = 0;
                                    int score = 0;
                                    if(totalScoreByPair[0] > totalScoreByPair[1]) {
                                        winnerTeam = 1;
                                        score = totalScoreByPair[0] - 36;
                                    } else if(totalScoreByPair[0] < totalScoreByPair[1]) {
                                        winnerTeam = 2;
                                        score = totalScoreByPair[1] - 36;
                                    }

                                    Object multiplier = g.getFlow().get("multiplier");
                                    if(multiplier == null) multiplier = "1";
                                    score = score * Integer.parseInt(multiplier.toString());   
                                    
                                    String trump = (String)g.getFlow().get("trump");
                                    if(trump != null && trump.equals("BOTIFARRA")) score = score * 2;
                                    

                                    Number pairPoints = 0;
                                    if(winnerTeam != 0) {
                                        pairPoints = (Number)g.getFlow().get("score_team" + winnerTeam);
                                        if(pairPoints == null) pairPoints = score;
                                        else pairPoints = pairPoints.intValue() + score;

                                        g.getFlow().put("score_team" + winnerTeam, pairPoints);
                                    }


                                    int maxScore = 101;
                                    String variant = g.getApplication().getName();
                                    int pos = variant.indexOf("-");
                                    if(pos != -1) {
                                        maxScore = Integer.parseInt(variant.substring(pos+1));
                                    }                                        


                                    if(pairPoints.intValue() >= maxScore) {
                                        g.setState(GroupState.FINISHED);
                                        rankFinishedGame(g, winnerTeam);                                            

                                    } else {
                                        internalData.clearRounds();        
                                        
                                        // el jugador que havia cantat trumfo abans, reparteix les cartes i el següent jugador comença cantant trumfo
                                        String lastTurn = g.getFlow().get("startTurn").toString();
                                        turn = (Integer.parseInt(lastTurn) + 1) % 4;  // següent jugador

                                        internalData.useNewDecks();
                                        while(!internalData.getAvailableCards().isEmpty()) {
                                            internalData.distributeCardsToPlayer(turn, 4);
                                            turn = (turn + 1) % 4;                                                                                        
                                        }

                                        g.setTurn(turn);
                                        
                                        g.getFlow().put("startTurn", String.valueOf(turn));
                                        g.getFlow().put("step", "trump");
                                        g.getFlow().put("multiplier", "1");
                                        g.getFlow().put("trump", "");                                        
                                        
                                        notifyPrivateData = true;
                                        
                                    }
                                    
                                }

                            }
                        
                            
                            g.setFlow(g.getFlow());
                            g.setInternalDataObject(internalData);
                            g.setData(getPublicData(g, internalData));

                            
                        }

                    }
                    break;
                }
            
                if(notifyPrivateData) {

                    HashSet<Long> memberSessions = new HashSet<Long>();

                    GroupAction action = new GroupAction();
                    action.setActionName(actionName);
                    action.setActionValue(actionValue);
                    action.setSlot(actionSlot);

                    WampDict event = g.toWampObject(true);
                    event.put("action", action.toWampObject());
                    event.put("cmd", "action");
                    event.put("members", module.getMembers(g.getGid(),0));
                    event.put("flow", g.getFlow());

                    String data = g.getData();
                    if(data != null) event.put("data", data); 

                    for(int index = 0; index <= 4; index++) {
                        // Update new PRIVATE DATA to each member
                        boolean publishEvent = false;
                        WampPublishOptions options = new WampPublishOptions();
                        event.remove("privateState");

                        if(index < 4) {
                            // members
                            Member member = g.getMember(index);
                            WampObject privateState = getPrivateState(g, member);

                            if(privateState != null) event.put("privateState", privateState);

                            HashSet<Long> eligible = new HashSet<Long>();
                            if(member.getClientSID() != null) {
                                memberSessions.add(member.getClientSID());
                                eligible.add(member.getClientSID());

                                options.setEligibleSessionIds(eligible);
                                publishEvent = true;
                            }

                        } else {
                            // specs
                            options.setExcludedSessionIds(memberSessions);  
                            publishEvent = true;
                        }

                        if(publishEvent) {
                            WampBroker.publishEvent(socket.getRealm(), WampProtocol.newGlobalScopeId(), WampBroker.getTopic(module.getFQtopicURI("group_event." + g.getGid())), null, event, options, null, true);
                        }
                    }
                }
            }
            
            
        } catch(Exception ex) {
            System.err.println("Error: " + ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            
        } finally {
            if(ruleEngine != null) {
                recycleRuleEngine(ruleEngine);
            }
        }        
        return isValid;
    }

    private void rankFinishedGame(Group g, int winnerTeam) throws Exception 
    {
        System.out.println("BotifarraCardGamesValidator: rankFinishedGame: WINNER=" + winnerTeam);
        
        Member m0Winner = g.getMember(winnerTeam-1);
        Member m1Loser = g.getMember(2-winnerTeam);
        Member m2Winner = g.getMember(winnerTeam-1 + 2);
        Member m3Loser = g.getMember(2-winnerTeam + 2);
        
        saveAchievement(g, m0Winner.getRole(), m0Winner.getUser(), "WIN", m1Loser.getUser().getUid());
        saveAchievement(g, m0Winner.getRole(), m0Winner.getUser(), "WIN", m3Loser.getUser().getUid());
        saveAchievement(g, m2Winner.getRole(), m2Winner.getUser(), "WIN", m1Loser.getUser().getUid());
        saveAchievement(g, m2Winner.getRole(), m2Winner.getUser(), "WIN", m3Loser.getUser().getUid());
        saveAchievement(g, m1Loser.getRole(), m1Loser.getUser(), "LOSE", m0Winner.getUser().getUid());
        saveAchievement(g, m1Loser.getRole(), m1Loser.getUser(), "LOSE", m2Winner.getUser().getUid());
        saveAchievement(g, m3Loser.getRole(), m3Loser.getUser(), "LOSE", m0Winner.getUser().getUid());
        saveAchievement(g, m3Loser.getRole(), m3Loser.getUser(), "LOSE", m2Winner.getUser().getUid());
        
        Ranking ranking = Ranking.getInstance(g.getApplication());
        ranking.addResult(m0Winner.getUser(), m1Loser.getUser(), 1.0);
        ranking.addResult(m0Winner.getUser(), m3Loser.getUser(), 1.0);
        ranking.addResult(m2Winner.getUser(), m1Loser.getUser(), 1.0);
        ranking.addResult(m2Winner.getUser(), m3Loser.getUser(), 1.0);
        ranking.updateRatings();
    }
    


    private ScriptEngine getRuleEngine(Collection<Application> apps) throws Exception
    {
        ScriptEngine ruleEngine = ruleEngines.poll();
        if(ruleEngine == null) {
            ScriptEngineManager factory = new ScriptEngineManager();
            ruleEngine = factory.getEngineByName("JavaScript");  // Graal.js

            ClassLoader cl = this.getClass().getClassLoader();
            //ruleEngine.eval(new InputStreamReader(cl.getResourceAsStream("META-INF/rules/move.js"),StandardCharsets.UTF_8));
            ruleEngine.eval(new InputStreamReader(cl.getResourceAsStream("META-INF/rules/game.js"),StandardCharsets.UTF_8));
            ruleEngine.eval(new InputStreamReader(cl.getResourceAsStream("META-INF/rules/botifarra.js"),StandardCharsets.UTF_8));

            ArrayList<Application> appsToLoad = new ArrayList<Application>();
            appsToLoad.addAll(apps);                
            Collections.sort(appsToLoad); 
            while(!appsToLoad.isEmpty()) {  // Note: inherited classes may fail until super class has been loaded
                Iterator<Application> iter = appsToLoad.iterator();
                while(iter.hasNext()) {
                    try { 
                        Application app = iter.next();
                        String appName = app.getName();
                        int pos = appName.indexOf("-");
                        if(pos != -1) {
                            appName = appName.substring(0, pos);
                        }
                        ruleEngine.eval(new InputStreamReader(cl.getResourceAsStream("META-INF/rules/" + appName +".js"),StandardCharsets.UTF_8)); 
                    } catch(Exception ex) { 
                        // skip exception
                    } finally {
                        iter.remove();
                    }
                    
                }
            }        
        }

        return ruleEngine;
    }    

    
    private void recycleRuleEngine(ScriptEngine ruleEngine)
    {
        if(ruleEngine != null) ruleEngines.offer(ruleEngine);
    }
    
    
    private int getRoundWinner(Group group, CardRound round) 
    {
        int winner = -1;
        Card bestPlayerCard = null;
        
        String trump = group.getFlow().get("trump").toString();

        for(PlayerCards playerCards : round.getExposedCards()) {
            List<Card> cards = playerCards.getCards();
            Card card = cards.get(0);
            if(bestPlayerCard == null 
                    || (!bestPlayerCard.getType().equals(trump) && card.getType().equals(trump))                     
                    || (card.getType().equals(bestPlayerCard.getType()) && getCardPriority(card) > getCardPriority(bestPlayerCard) ) ) 
            {
                if(trump.equals("BOTIFARRA") && bestPlayerCard == null) trump = card.getType();
                
                winner = playerCards.getPlayer();
                bestPlayerCard = card;
            }
        }
        
        return winner;
    }
    
    
    private int getCardPriority(Card card) 
    {
        int priority = getCardScore(card);
        if(priority == 0) priority = Integer.parseInt(card.getValue());
        else priority += 10;
        
        return priority;
    }
    
    
    private int getCardScore(Card card) 
    {
        int cardScore = 0;        
        String val = card.getValue();

        switch(val) {
            case "9": cardScore = 5; break; // Manilla 
            case "1": cardScore = 4; break; // As
            case "K": cardScore = 3; break; // Rei
            case "Q": cardScore = 2; break; // Caball
            case "J": cardScore = 1; break; // Sota
        }

        return cardScore;
    }
    
    private int getRoundScore(CardRound round) 
    {
        int ret = 1;
        
        for(PlayerCards playerCards : round.getExposedCards()) {
            List<Card> cards = playerCards.getCards();  // but only 1 card expected for player in this game
            Card card = cards.get(0);
            Number cardScore = getCardScore(card);
            ret += cardScore.intValue();
        }
        
        return ret;
    }
    
    private Map<String, Object> filterFlowDataForCardRound(Map<String, Object> map)
    {
        String cardRoundKeys[] = { "multiplier", "trump" };
        HashMap<String, Object> newMap = new HashMap<String, Object>();
        
        for(String key : cardRoundKeys) {
            newMap.put(key, map.get(key));
        }
        
        return newMap;
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
        
    private String getPublicData(Group g, GroupInternalDataForCardGame internalData)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(g.getApplication().getName().replace("-", "#"));
        sb.append("#");
        sb.append(g.getTurn());
        sb.append("#");
        sb.append(g.getFlow().getOrDefault("step", "INIT").toString());
        sb.append("#");
        sb.append(g.getFlow().getOrDefault("trump", "NONE").toString());
        sb.append("#");
        sb.append(g.getFlow().getOrDefault("multiplier", "1").toString());
        sb.append("#");
        sb.append(g.getFlow().getOrDefault("ask_pair_to_double", "NO").toString());
        sb.append("#");
        sb.append(g.getFlow().getOrDefault("score_team1", "0").toString());
        sb.append("#");
        sb.append(g.getFlow().getOrDefault("score_team2", "0").toString());
        sb.append("#");
        sb.append(getRoundData(internalData.getCurrentRound()));
        sb.append("#");        
        sb.append(g.getFlow().getOrDefault("lastRound", "").toString());
        sb.append("#");        
        sb.append(g.getAdmin().getUid());
        
        return sb.toString();
    }
    
    private String getRoundData(CardRound round)
    {
        if(round == null) {
            return "";
        } else {
            StringBuilder sb = new StringBuilder();
            Card list[] = new Card[4];

            int count = 0;
            int playerToStartRound = -1;
            for(PlayerCards playerCards : round.getExposedCards()) {
                if(count == 0) playerToStartRound = playerCards.getPlayer();
                list[playerCards.getPlayer()] = playerCards.getCards().get(0);
            }

            for(Card card : list) {
                if(card != null) {
                    sb.append(card.toString()); // value_type
                } else {
                    sb.append("NONE");
                }
                sb.append(',');            
            }

            sb.append(round.getFlowData().getOrDefault("trump", "NONE"));
            sb.append(',');      
            sb.append(playerToStartRound);    
            sb.append(',');        
            sb.append(String.valueOf(round.getScore()));
            sb.append(',');              
            sb.append(round.getWinner());        

            return sb.toString();
        }
    }
    
    
}
