package org.wgs.service.game.tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.wgs.security.User;
import org.wgs.service.game.Ranking;


public class RankingTest {
    
    public RankingTest() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void test1() throws Exception {
        Ranking ranking = Ranking.getInstance(0.5);
        
        User p1 = new User(); p1.setUid("p1");
        User p2 = new User(); p2.setUid("p2");
        User p3 = new User(); p3.setUid("p3");
        User p4 = new User(); p4.setUid("p4");
        
        ranking.addPlayer(p1, ranking.newRating(p1, 1500.0, 200.0, 0.06));
        ranking.addPlayer(p2, ranking.newRating(p2, 1400.0, 30.0,  0.06));
        ranking.addPlayer(p3, ranking.newRating(p3, 1550.0, 100.0, 0.06));
        ranking.addPlayer(p4, ranking.newRating(p4, 1700.0, 300.0, 0.06));
        
        ranking.addResult(p1, p2, 1);
        ranking.addResult(p1, p3, 0);
        ranking.addResult(p1, p4, 0);
        
        ranking.updateRatings();
        
    }
    
    @Test
    public void test2() throws Exception {
        Ranking ranking = Ranking.getInstance(0.5);
        
        User p1 = new User(); p1.setUid("p1");
        User p2 = new User(); p2.setUid("p2");
        User p3 = new User(); p3.setUid("p3");
        User p4 = new User(); p4.setUid("p4");
        
        ranking.addPlayer(p1, ranking.newRating(p1, 1500.0, 350.0, 0.06));
        ranking.addPlayer(p2, ranking.newRating(p2, 1500.0, 350.0, 0.06));
        ranking.addPlayer(p3, ranking.newRating(p3, 1500.0, 350.0, 0.06));
        ranking.addPlayer(p4, ranking.newRating(p4, 1500.0, 350.0, 0.06));
        
        ranking.addResult(p1, p2, 1);
        ranking.addResult(p1, p3, 0);
        ranking.addResult(p1, p4, 0);
        
        ranking.addResult(p1, p2, 1);
        ranking.addResult(p1, p3, 1);
        ranking.addResult(p1, p4, 0);
        
        ranking.addResult(p1, p2, 1);
        ranking.addResult(p1, p3, 0);
        ranking.addResult(p1, p4, 1);
        
        ranking.addResult(p1, p2, 0);
        ranking.addResult(p1, p3, 1);
        ranking.addResult(p1, p4, 0);
        
        ranking.addResult(p1, p2, 0);
        ranking.addResult(p1, p3, 0);
        ranking.addResult(p1, p4, 0);
        
        ranking.addResult(p1, p2, 1);
        ranking.addResult(p1, p3, 1);
        ranking.addResult(p1, p4, 1);
        
        ranking.updateRatings();
        
    }    
}
