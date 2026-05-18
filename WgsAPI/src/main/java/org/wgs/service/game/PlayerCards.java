/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.wgs.service.game;

import java.io.Serializable;
import java.util.List;

/**
 *
 * @author jordi
 */
public class PlayerCards implements Serializable 
{
    private static final long serialVersionUID = 1L;
    
    private int player;
    private List<Card> cards;
    
    public PlayerCards()    
    { 
        player = -1;
        cards = null;
    }
    
    public PlayerCards(int player, List<Card> cards)
    { 
        this.player = player;
        this.cards = cards;
    }    

    
    /**
     * @return the player
     */
    public int getPlayer() {
        return player;
    }

    /**
     * @param player the player to set
     */
    public void setPlayer(int player) {
        this.player = player;
    }

    /**
     * @return the cards
     */
    public List<Card> getCards() {
        return cards;
    }

    /**
     * @param cards the cards to set
     */
    public void setCards(List<Card> cards) {
        this.cards = cards;
    }
}
