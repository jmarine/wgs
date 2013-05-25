package org.wampservices.wgs;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;


@Entity
@Table(name="APP_LEADERBOARD")
@IdClass(LeaderBoardPK.class)
public class LeaderBoard implements java.io.Serializable
{
    
    @Id
    @ManyToOne(fetch=FetchType.LAZY)
    //@JoinColumn(name="app_id", referencedColumnName="id")
    private Application application;    

    
    @Id
    @Column(name="leaderboard_id")
    private int id;
    

    @OneToMany(mappedBy = "leaderBoard", fetch=FetchType.EAGER, cascade = { CascadeType.ALL })
    @OrderBy("position")
    private ArrayList<Score> scores = new ArrayList<Score>();    
    
    
    /**
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(int id) {
        this.id = id;
    }
    
    
    /**
     * @return the application
     */
    public Application getApplication() {
        return application;
    }

    /**
     * @param application the application to set
     */
    public void setApplication(Application application) {
        this.application = application;
    }
    
    
    public List<Score> getScores() 
    {
        return this.scores;
    }
    
    public void setScores(List<Score> scores) 
    {
        this.scores = new ArrayList<Score>();
        for(Score score : scores) {
            setScore(score.getPosition(), score);
        }
    }    
    
    public Score getScore(int index) 
    {
        List<Score> scores = getScores();
        if(index < scores.size()) {
            return scores.get(index);
        } else {
            return null;
        }
    }

    public void setScore(int index, Score score) 
    {
        List<Score> scores = getScores();
        while(index >= scores.size()) {
            scores.add(null);
        }
        score.setPosition(index);
        scores.set(index, score);
    }

    public Score removeScore(int index) 
    {
        Score removed = getScore(index);
        int last = getScores().size();
        for(; index < last-1; index++) {
            setScore(index, getScore(index+1));
        }      
        if(index < last) scores.remove(index);
        return removed;
    }    


    
}
