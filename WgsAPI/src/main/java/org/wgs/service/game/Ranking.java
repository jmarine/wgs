package org.wgs.service.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import org.wgs.security.User;
import org.wgs.util.Storage;


public class Ranking extends Glicko2
{
    private static HashMap<Application,Ranking> rankings = new HashMap<Application,Ranking>();
    
    private EntityManager manager;
    private EntityTransaction tx;
    private Application app;
    
    private TreeSet<Rating> ratingsInOrder = new TreeSet<Rating>();
    private HashMap<User,Rating> ratingsByUser = new HashMap<User,Rating>();
    

    private Ranking(Application app)
    {
        super(Glicko2.DEFAULT_TAU);
        this.app = app;
    }

    
    private Ranking(Application app, double tau)
    {
        super(tau);
        this.app = app;
    }    
    

    public static Ranking getInstance(Application app) throws Exception
    {
        Ranking r = rankings.get(app);
        if(r == null) {
            r = new Ranking(app);
            if(r.loadRatings() == 0) {
                r.updateFromAchievements();
            }
            rankings.put(app, r);
        }
        return r;
    }
    
    public static Ranking getInstance(double tau)
    {
        return new Ranking(null, tau);
    }
        
    
   
    public ArrayList<Rating> getTopRatings(int count)
    {
        ArrayList<Rating> retval = new ArrayList<Rating>();
        Iterator<Rating> iter = ratingsInOrder.iterator();
        for(int index = 0; index < count && iter.hasNext(); index++) {
            retval.add(iter.next());
        }
        return retval;
    }
    
    
    public int getUserRankingPosition(User player)
    {
        if(!ratingsByUser.containsKey(player)) {
            return 0;
        } else {
            return 1+ratingsInOrder.headSet(ratingsByUser.get(player)).size();
        }
    }
    
    
    
    public Rating getUserRating(User player)
    {
        EntityManager manager = getEntityManager();
        Achievement ratingAchievement = null;
        Achievement rdAchievement = null;
        Achievement volAchievement = null;
        
        try {
            TypedQuery<Achievement> query = manager.createNamedQuery("wgs.findAppUserAchievement", Achievement.class);
            query.setParameter(1, app);
            query.setParameter(2, player);
            query.setParameter(3, "GLICKO-RAT");
            ratingAchievement = query.getSingleResult();
            query.setParameter(3, "GLICKO-RD");
            rdAchievement = query.getSingleResult();
            query.setParameter(3, "GLICKO-VOL");
            volAchievement = query.getSingleResult();
        } catch(Exception ex) {
            System.out.println("Player was not rated: " + player.getName());
        }

        Rating r = newRating(player, Glicko2.DEFAULT_RATING, Glicko2.DEFAULT_DEVIATION, Glicko2.DEFAULT_VOLATILITY);
        if(ratingAchievement != null) r.setRating(Double.parseDouble(ratingAchievement.getValue()));
        if(rdAchievement != null) r.setRatingDeviation(Double.parseDouble(rdAchievement.getValue()));
        if(volAchievement != null) r.setVolatility(Double.parseDouble(volAchievement.getValue()));

        return r;
    }    
        
    
    @Override
    public void addResult(User p1, User p2, double value)
    {
        if(!hasPlayer(p1)) addPlayer(p1, getUserRating(p1));
        if(!hasPlayer(p2)) addPlayer(p2, getUserRating(p2));
        super.addResult(p1, p2, value);
    }
    
    
    public int loadRatings() 
    {
        EntityManager manager = getEntityManager();
        TypedQuery<User> query = manager.createQuery("SELECT DISTINCT a.sourceUser FROM Achievement a WHERE a.app = ?1 and a.name = 'GLICKO-RAT'", User.class);
        query.setParameter(1, app);
        for(User player : query.getResultList()) {
            Rating rating = getUserRating(player);
            ratingsInOrder.add(rating);
            ratingsByUser.put(player, rating);
        }
        return ratingsByUser.size();
    }
            

    public void updateFromAchievements() throws Exception
    {
        ArrayList<String> gids = new ArrayList<String>();
        EntityManager manager = getEntityManager();
        Query delete = manager.createQuery("DELETE FROM Achievement a WHERE a.app = ?1 and a.name LIKE 'GLICKO-%'");
        delete.setParameter(1, app);
        int rows = delete.executeUpdate();
        
        TypedQuery<Achievement> query = manager.createQuery("SELECT OBJECT(a) FROM Achievement a WHERE a.app = ?1 and a.name IN ('WIN','DRAW')", Achievement.class);
        query.setParameter(1, app);
        for(Achievement a : query.getResultList()) {
            if(!gids.contains(a.getGid())) {
                User p1 = a.getSourceUser();
                User p2 = manager.find(User.class, a.getValue());
                addResult(p1, p2, ("WIN".equals(a.getName()))? 1.0 : 0.5);
                gids.add(a.getGid());
            }
        }
        updateRatings();
    }    
        
    
    
    @Override
    public HashMap<User, Rating> updateRatings() throws Exception
    {
        HashMap<User, Rating> updatedRatingsByUser = super.updateRatings();
        
        if(manager != null) {
            for(User player : updatedRatingsByUser.keySet()) {
                Rating updatedRating = updatedRatingsByUser.get(player);
                saveUserRating(app, player, updatedRating);
                
                Rating old = ratingsByUser.put(player, updatedRating);
                if(old != null) ratingsInOrder.remove(old);
                ratingsInOrder.add(updatedRating);
            }

            tx.commit();
            
            manager.close();
            manager = null;
        }       
        
        return updatedRatingsByUser;
    }
    

    
    
    private void saveUserRating(Application app, User player, Rating item)
    {
        EntityManager manager = getEntityManager();
        TypedQuery<Achievement> query = manager.createNamedQuery("wgs.findAppUserAchievement", Achievement.class);
        query.setParameter(1, app);
        query.setParameter(2, player);
        query.setParameter(3, "GLICKO-RAT");
        Achievement ratingAchievement = null;
        try { 
            ratingAchievement = query.getSingleResult(); 
        } catch(javax.persistence.NoResultException ex) {
            ratingAchievement = new Achievement();
            ratingAchievement.setApp(app);
            ratingAchievement.setSourceUser(player);
            ratingAchievement.setName("GLICKO-RAT");
        }
        ratingAchievement.setValue(String.valueOf(item.getRating()));
        manager.merge(ratingAchievement);

        query.setParameter(3, "GLICKO-RD");
        Achievement rdAchievement = null;
        try {
            rdAchievement = query.getSingleResult();
        } catch(javax.persistence.NoResultException ex) {
            rdAchievement = new Achievement();
            rdAchievement.setApp(app);
            rdAchievement.setSourceUser(player);
            rdAchievement.setName("GLICKO-RD");
        }
        rdAchievement.setValue(String.valueOf(item.getRatingDeviation()));
        manager.merge(rdAchievement);

        query.setParameter(3, "GLICKO-VOL");
        Achievement volAchievement = null;
        try {
            volAchievement = query.getSingleResult();
        } catch(javax.persistence.NoResultException ex) {
            volAchievement = new Achievement();
            volAchievement.setApp(app);
            volAchievement.setSourceUser(player);
            volAchievement.setName("GLICKO-VOL");
        }      
        volAchievement.setValue(String.valueOf(item.getVolatility()));
        manager.merge(volAchievement);
            
    }
    

    private EntityManager getEntityManager()
    {
        if(manager == null) {
            manager = Storage.getEntityManager();
            tx = manager.getTransaction();
            tx.begin();
        }
        return manager;
    }
    
}    
    

// The Glicko2 class contains code from Jeremy Gooch's Java implementation of the Glicko-2 rating algorithm 
// Source: https://github.com/goochjs/glicko2
/**
Copyright (c) 2013, Jeremy Gooch
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met: 

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer. 
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution. 

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
class Glicko2 
{
    public  final static double DEFAULT_RATING =  1500.0;
    public  final static double DEFAULT_DEVIATION =  350;
    public  final static double DEFAULT_VOLATILITY =  0.06;
    public  final static double DEFAULT_TAU =  0.75;
    private final static double MULTIPLIER =  173.7178;
    private final static double CONVERGENCE_TOLERANCE =  0.000001;    

    private double tau;   

    private HashMap<User, Rating> glicko2RatingsByUser = new HashMap<User, Rating>();    
    private HashMap<User, List<Result>> resultsByUser = new HashMap<User, List<Result>>();


    public Glicko2(double tau)
    {
        this.tau = tau;
    }
    
    public void clear()
    {
        glicko2RatingsByUser = new HashMap<User, Rating>();    
        resultsByUser = new HashMap<User, List<Result>>();
    }

    public Set<User> getPlayers()
    {
        return resultsByUser.keySet();
    }

    public boolean hasPlayer(User player)
    {
        return resultsByUser.containsKey(player);
    }

    public void addPlayer(User player, Rating playerGlickoRating)
    {
        List<Result> results = resultsByUser.get(player);
        if(results == null) {
            results = new ArrayList<Result>();
            resultsByUser.put(player, results);
        }
        
        glicko2RatingsByUser.put(player, newRating(player, 
                                                   convertRatingToGlicko2Scale(playerGlickoRating.getRating()), 
                                                   convertRatingDeviationToGlicko2Scale(playerGlickoRating.getRatingDeviation()),
                                                   playerGlickoRating.getVolatility()));
    }

    
    public Rating newRating(User user, double r, double rd, double v)
    {
        return new Rating(user, r, rd, v);
    }  
    

    public void addResult(User p1, User p2, double value)
    {
        Result result = new Result(p1, p2, value);
        resultsByUser.get(p1).add(result);
        resultsByUser.get(p2).add(result);
    }    

    

    public HashMap<User, Rating> updateRatings() throws Exception
    {
        HashMap<User, Rating> updatedRatingsByUser = new HashMap<User, Rating>();

        for(Map.Entry<User,List<Result>> entry : resultsByUser.entrySet())
        {
            User player = entry.getKey();
            List<Result> playerResults = entry.getValue();

            Rating glicko2Rating = glicko2RatingsByUser.get(player);
            if ( playerResults.size() > 0 ) {
                glicko2Rating = calculateNewRating(player, playerResults);
            } else {
                glicko2Rating = newRating(player,
                                          glicko2Rating.getRating(),
                                          calculateNewRD(glicko2Rating.getRatingDeviation(), glicko2Rating.getVolatility()),
                                          glicko2Rating.getVolatility());
            }       

            
            updatedRatingsByUser.put(entry.getKey(), newRating(player,
                                                               convertRatingToOriginalGlickoScale(glicko2Rating.getRating()), 
                                                               convertRatingDeviationToOriginalGlickoScale(glicko2Rating.getRatingDeviation()),
                                                               glicko2Rating.getVolatility()));
        }
        
        
        debug("New ratings:");
        for(User player : getPlayers()) {
            debug(player.toString() + " : " + updatedRatingsByUser.get(player));
        }
        
        clear();
        
        return updatedRatingsByUser;
        
    }    
    
    public void debug(String msg)
    {
        System.out.println(msg);
    }



    /**
     * This is the function processing described in step 5 of Glickman's paper.
     *  
     * @param player
     * @param results
     */
    private Rating calculateNewRating(User player, List<Result> results) throws Exception
    {
        Rating glicko2Rating = glicko2RatingsByUser.get(player);
        double mu = glicko2Rating.getRating();
        double phi = glicko2Rating.getRatingDeviation();
        double sigma = glicko2Rating.getVolatility();
        double a = Math.log( Math.pow(sigma, 2) );
        double delta = delta(player, results);
        double v = v(player, results);

        // step 5.2 - set the initial values of the iterative algorithm to come in step 5.4
        double A = a;
        double B = 0.0;
        if ( Math.pow(delta, 2) > Math.pow(phi, 2) + v ) {
                B = Math.log( Math.pow(delta, 2) - Math.pow(phi, 2) - v );			
        } else {
                double k = 1;
                B = a - ( k * Math.abs(tau));

                while ( f(B , delta, phi, v, a, tau) < 0 ) {
                        k++;
                        B = a - ( k * Math.abs(tau));
                }
        }

        // step 5.3
        double fA = f(A , delta, phi, v, a, tau);
        double fB = f(B , delta, phi, v, a, tau);

        // step 5.4
        while ( Math.abs(B - A) > CONVERGENCE_TOLERANCE ) {
                double C = A + (( (A-B)*fA ) / (fB - fA));
                double fC = f(C , delta, phi, v, a, tau);

                if ( fC * fB < 0 ) {
                        A = B;
                        fA = fB;
                } else {	
                        fA = fA / 2.0;
                }

                B = C;
                fB = fC;
        }

        double newSigma = Math.exp( A/2.0 );

        // Step 6
        double phiStar = calculateNewRD( phi, newSigma );

        // Step 7
        double newPhi = 1.0 / Math.sqrt(( 1.0 / Math.pow(phiStar, 2) ) + ( 1.0 / v ));

        return newRating(player, mu+(Math.pow(newPhi, 2)*outcomeBasedRating(player, results)), newPhi, newSigma);

   }

   private double f(double x, double delta, double phi, double v, double a, double tau) {
           return ( Math.exp(x) * ( Math.pow(delta, 2) - Math.pow(phi, 2) - v - Math.exp(x) ) /
                           (2.0 * Math.pow( Math.pow(phi, 2) + v + Math.exp(x), 2) )) - 
                           ( ( x - a ) / Math.pow(tau, 2) );
   }


   /**
    * This is the first sub-function of step 3 of Glickman's paper.
    * 
    * @param deviation
    * @return
    */
   private double g(double deviation) {
           return 1.0 / ( Math.sqrt( 1.0 + ( 3.0 * Math.pow(deviation, 2) / Math.pow(Math.PI,2) )));
   }


   /**
    * This is the second sub-function of step 3 of Glickman's paper.
    * 
    * @param playerRating
    * @param opponentRating
    * @param opponentDeviation
    * @return
    */
   private double E(double playerRating, double opponentRating, double opponentDeviation) {
           return 1.0 / (1.0 + Math.exp( -1.0 * g(opponentDeviation) * ( playerRating - opponentRating )));
   }


   /**
    * This is the main function in step 3 of Glickman's paper.
    * 
    * @param player
    * @param results
    * @return
    */
   private double v(User player, List<Result> results) throws Exception {
           double v = 0.0;
           Rating playerRating = glicko2RatingsByUser.get(player);

           for ( Result result: results ) {
                Rating opponentRating = glicko2RatingsByUser.get(result.getOpponent(player));

                   v = v + (
                                   ( Math.pow( g(opponentRating.getRatingDeviation()), 2) )
                                   * E(playerRating.getRating(),
                                                   opponentRating.getRating(),
                                                   opponentRating.getRatingDeviation())
                                   * ( 1.0 - E(playerRating.getRating(),
                                                   opponentRating.getRating(),
                                                   opponentRating.getRatingDeviation())
                                   ));
           }

           return Math.pow(v, -1);
   }


   /**
    * This is a formula as per step 4 of Glickman's paper.
    * 
    * @param player
    * @param results
    * @return delta
    */
   private double delta(User player, List<Result> results) throws Exception {
           return v(player, results) * outcomeBasedRating(player, results);
   }


   /**
    * This is a formula as per step 4 of Glickman's paper.
    * 
    * @param player
    * @param results
    * @return expected rating based on game outcomes
    */
   private double outcomeBasedRating(User player, List<Result> results) throws Exception {
       Rating playerRating = glicko2RatingsByUser.get(player);


           double outcomeBasedRating = 0;

           for ( Result result: results ) {
               Rating opponentRating = glicko2RatingsByUser.get(result.getOpponent(player));
               
                   outcomeBasedRating = outcomeBasedRating
                                   + ( g(opponentRating.getRatingDeviation())
                                           * ( result.getScore(player) - E(
                                                           playerRating.getRating(),
                                                           opponentRating.getRating(),
                                                           opponentRating.getRatingDeviation() ))
                           );
           }

           return outcomeBasedRating;
   }


   /**
    * This is the formula defined in step 6. It is also used for players
    * who have not competed during the rating period.
    * 
    * @param phi
    * @param sigma
    * @return new rating deviation
    */
   private double calculateNewRD(double phi, double sigma) {
           return Math.sqrt( Math.pow(phi, 2) + Math.pow(sigma, 2) );
   }


   /**
    * Converts from the value used within the algorithm to a rating in the same range as traditional Elo et al
    * 
    * @param rating in Glicko2 scale
    * @return rating in Glicko scale
    */
   public double convertRatingToOriginalGlickoScale(double rating) {
           return ( ( rating  * MULTIPLIER ) + DEFAULT_RATING );
   }


   /**
    * Converts from a rating in the same range as traditional Elo et al to the value used within the algorithm
    * 
    * @param rating in Glicko scale
    * @return rating in Glicko2 scale
    */
   public double convertRatingToGlicko2Scale(double rating) {
           return ( ( rating  - DEFAULT_RATING ) / MULTIPLIER ) ;
   }


   /**
    * Converts from the value used within the algorithm to a rating deviation in the same range as traditional Elo et al
    * 
    * @param ratingDeviation in Glicko2 scale
    * @return ratingDeviation in Glicko scale
    */
   public double convertRatingDeviationToOriginalGlickoScale(double ratingDeviation) {
           return ( ratingDeviation * MULTIPLIER ) ;
   }


   /**
    * Converts from a rating deviation in the same range as traditional Elo et al to the value used within the algorithm
    * 
    * @param ratingDeviation in Glicko scale
    * @return ratingDeviation in Glicko2 scale
    */
   public double convertRatingDeviationToGlicko2Scale(double ratingDeviation) { 
           return ( ratingDeviation / MULTIPLIER );
   }    


    
    public class Result
    {
        private User player1;
        private User player2;
        private double value;
        
        public Result(User player1, User player2, double value)
        {
            this.player1 = player1;
            this.player2 = player2;
            this.value = value;
        }
        
        
        public User getPlayer1()
        {
            return player1;
        }
        
        public User getPlayer2()
        {
            return player2;
        }
        
        public User getOpponent(User player) throws Exception
        {
            if(player1.equals(player)) {
                return player2;
            } else if(player2.equals(player)) {
                return player1;
            } else {
                throw new Exception("Result not from player");
            }
        }
        
        public double getScore(User player) throws Exception
        {
            if(player.equals(player1)) {
                return value;
            } else if(player.equals(player2)) {
                return 1-value;
            } else {
                throw new Exception("The user " + player + " is not participant of the result: " + toString());
            }
        }
        
    }
    
    public class Rating implements Comparable
    {
        User   player;
        double rating;
        double ratingDeviation;
        double volatility;
        
        public Rating(User user, double rating, double ratingDeviation, double volatility)
        {
            this.player = user;
            this.rating = rating;
            this.ratingDeviation = ratingDeviation;
            this.volatility = volatility;  // TODO: get value from application settings
        }
        
        public User getPlayer()
        {
            return player;
        }
        
        public void setRating(double rating)
        {
            this.rating = rating;
        }
        
        public double getRating()
        {
            return rating;
        }
        
        
        public void setRatingDeviation(double ratingDeviation)
        {
            this.ratingDeviation = ratingDeviation;
        }
        
        public double getRatingDeviation()
        {
            return ratingDeviation;
        }

        
        public void setVolatility(double volatility)
        {
            this.volatility = volatility;
        }        
        
        public double getVolatility()
        {
            return volatility;
        }
        
        
        
        public String toString()
        {
            java.text.DecimalFormat df2 = new java.text.DecimalFormat("0.00");
            java.text.DecimalFormat df6 = new java.text.DecimalFormat("0.00####");
            return df2.format(rating) + "±" + df2.format(ratingDeviation) + " (" + df6.format(volatility) + ")";
        }

        @Override
        public int compareTo(Object o) 
        {
            Rating r = (Rating)o;
            if(rating == r.getRating() && ratingDeviation == r.getRatingDeviation() && volatility == r.getVolatility()) {
                return player.getName().compareTo(r.getPlayer().getName());
            } else if(rating > r.getRating() || (rating == r.getRating() && ratingDeviation < r.getRatingDeviation()) 
                    || (rating == r.getRating() && ratingDeviation == r.getRatingDeviation() && volatility < r.getVolatility()) ) {
                return -1;
            } else { 
                return 1;
            }
        }
        
        @Override
        public boolean equals(Object o)
        {
            Rating r = (Rating)o;
            return (player.equals(r.getPlayer()));
        }
        
        @Override
        public int hashCode()
        {
            return player.hashCode();
        }
    }
    
}
