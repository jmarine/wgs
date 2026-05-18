package org.wgs.service.game;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CardRound implements Serializable 
{
    private int winner;
    
    private double score;
    
    private Map<String, Object> flowData;
    
    private List<PlayerCards> exposedCards;
        
    
    public CardRound()
    {
        winner = -1;
        score = 0.0;
        exposedCards = new ArrayList<PlayerCards>();
        flowData = new HashMap<String, Object>();
    }
    
    /**
     * @return the winner
     */
    public int getWinner() {
        return winner;
    }

    /**
     * @param winner the winner to set
     */
    public void setWinner(int winner) {
        this.winner = winner;
    }

    
    /**
     * @return the exposedCardsOfPlayer
     */
    public List<PlayerCards> getExposedCards() {
        return exposedCards;
    }
    

    /**
     * @param exposedCardsByPlayer the exposedCardsOfPlayer to set
     */
    public void setExposedCards(List<PlayerCards> exposedCards) {
        this.exposedCards = exposedCards;
    }
    
    public void addExposedCardsOfPlayer(int player, List<Card> cards)
    {
        PlayerCards playerCards = new PlayerCards(player, cards);
        exposedCards.add(playerCards);
    }

    
    /**
     * @return the flowData
     */
    public Map<String, Object> getFlowData() {
        return flowData;
    }

    /**
     * @param flowData the flowData to set
     */
    public void setFlowData(Map<String, Object> flowData) {
        this.flowData = flowData;
    }


    /**
     * @return the score
     */
    public double getScore() {
        return score;
    }

    /**
     * @param score the score to set
     */
    public void setScore(double score) {
        this.score = score;
    }
    
}
