package org.wgs.service.game;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class GroupInternalDataForCardGame implements GroupInternalData
{
    private int numberOfDecks;
    private String types[];
    private String values[];
    
    private List<Card> availableCards;
    private List<Card> usedCards;
    private Map<Integer, LinkedList<Card>> privateCards;
    private Map<Integer, LinkedList<Card>> publicCards;
    
    private CardRound currentRound;
    private List<CardRound> rounds;
    private Map<Integer, Double> points;
    
    public GroupInternalDataForCardGame() { }
    
    public void init(Map<String,Object> options)
    {
        numberOfDecks = Integer.parseInt((String)options.get("num_decks"));
        types = ((String)options.get("types")).split(",");
        values = ((String)options.get("values")).split(",");

        setPoints(new HashMap<Integer, Double>());
        clearRounds();
        
        useNewDecks();
        
        newCardRound();

    }
    
    public void clearRounds()
    {
        setRounds(new LinkedList<CardRound>());
    }
    
    public void useNewDecks()
    {
        setAvailableCards(new LinkedList<Card>());
        setUsedCards(new LinkedList<Card>());
        
        setPrivateCards(new HashMap<Integer, LinkedList<Card>>());
        setPublicCards(new HashMap<Integer, LinkedList<Card>>());

        
        for(int i = 0; i < numberOfDecks; i++) {
        
            if(values == null || values.length <= 1) {

                // encoded set of cards
                // ie: sushi go = 14xTempura, 14xSashimi, 14xDumpling, 12x2Makis, 8x3Makis, 6x1Maki, 10xSalmon, 5xSquid, 5xEggOmelet, 10xDessert, 6xWasabi, 4xChopsticks
                for(var type : types) {
                    int count = 1;
                    if(type.contains("x")) {
                        String parts[] = type.split("x");
                        if(parts[0].length() > 0 && Character.isDigit(parts[0].charAt(0))) {
                            type = parts[1];
                            try { count = Integer.parseInt(parts[0]); } 
                            catch(Exception ex) { }
                        } else if(parts[1].length() > 0 && Character.isDigit(parts[1].charAt(0))) {
                            type = parts[0];
                            try { count = Integer.parseInt(parts[1]); } 
                            catch(Exception ex) { }
                        }
                    }

                    for(; count > 0; count--) {
                        Card c = new Card();
                        c.setType(type);
                        c.setValue((values != null && values.length == 1) ? values[0] : "1");
                        getAvailableCards().add(c);
                    }
                }

            } else {

                // generate cards for all values * types 
                for(var value : values) {
                    for(var type : types) {
                        Card c = new Card();
                        c.setValue(value);
                        c.setType(type);
                        getAvailableCards().add(c);
                    }
                }
            }
        }
        
        shuffle();        
    }
    
    
    /**
     * @return the numberOfDecks
     */
    public int getNumberOfDecks() {
        return numberOfDecks;
    }

    /**
     * @param numberOfDecks the numberOfDecks to set
     */
    public void setNumberOfDecks(int numberOfDecks) {
        this.numberOfDecks = numberOfDecks;
    }

    /**
     * @return the types
     */
    public String[] getTypes() {
        return types;
    }

    /**
     * @param types the types to set
     */
    public void setTypes(String[] types) {
        this.types = types;
    }

    /**
     * @return the values
     */
    public String[] getValues() {
        return values;
    }

    /**
     * @param values the values to set
     */
    public void setValues(String[] values) {
        this.values = values;
    }

    /**
     * @return the availableCards
     */
    public List<Card> getAvailableCards() {
        return availableCards;
    }

    /**
     * @param availableCards the availableCards to set
     */
    public void setAvailableCards(List<Card> availableCards) {
        this.availableCards = availableCards;
    }

    /**
     * @return the usedCards
     */
    public List<Card> getUsedCards() {
        return usedCards;
    }

    /**
     * @param usedCards the usedCards to set
     */
    public void setUsedCards(List<Card> usedCards) {
        this.usedCards = usedCards;
    }

    /**
     * @return the privateCards
     */
    public Map<Integer, LinkedList<Card>> getPrivateCards() {
        return privateCards;
    }

    /**
     * @param privateCards the privateCards to set
     */
    public void setPrivateCards(Map<Integer, LinkedList<Card>> privateCards) {
        this.privateCards = privateCards;
    }
    
    

  

    /**
     * @return the publicCards
     */
    public Map<Integer, LinkedList<Card>> getPublicCards() {
        return publicCards;
    }

    /**
     * @param publicCards the publicCards to set
     */
    public void setPublicCards(Map<Integer, LinkedList<Card>> publicCards) {
        this.publicCards = publicCards;
    }

    /**
     * @return the currentRound
     */
    public CardRound getCurrentRound() {
        return currentRound;
    }

    /**
     * @param currentRound the currentRound to set
     */
    public void setCurrentRound(CardRound currentRound) {
        this.currentRound = currentRound;
    }

    /**
     * @return the rounds
     */
    public List<CardRound> getRounds() {
        return rounds;
    }

    /**
     * @param rounds the rounds to set
     */
    public void setRounds(List<CardRound> rounds) {
        this.rounds = rounds;
    }

    /**
     * @return the points
     */
    public Map<Integer, Double> getPoints() {
        return points;
    }

    /**
     * @param points the points to set
     */
    public void setPoints(Map<Integer, Double> points) {
        this.points = points;
    }
    
    
    public void newCardRound()
    {
        setCurrentRound(new CardRound());
    }
    
    public void shuffle()
    {
        SecureRandom sr = new SecureRandom();
        int len = getAvailableCards().size();
        for(int i = 0; i < len-2; i++) {
            int pos = sr.nextInt(i+1, len-1);
            if(pos > 0) {
                Card c = getAvailableCards().remove(pos);
                getAvailableCards().add(i, c);
            }
        }
    }
    
    public List<Card> distributeCardsToPlayer(int player, int count)
    {
        LinkedList<Card> distributedCards = new LinkedList<Card>();
        
        shuffle();
        LinkedList<Card> playerCards = getPrivateCards().getOrDefault(player, new LinkedList<Card>());        
        for(int i = 0; i < count; i++) {
            Card c = getAvailableCards().remove(0);
            distributedCards.add(c);
            playerCards.add(c);
        }
        getPrivateCards().put(player, playerCards);
        
        return distributedCards;
    }
    
    public boolean disposeCardFromPlayer(int player, Card c) 
    {
        List<Card> playerCards = getPrivateCards().get(player);
        if(playerCards != null && playerCards.remove(c)) {
            getUsedCards().add(c);
            return true;
        } else {
            return false;
        }
    }
    
}
