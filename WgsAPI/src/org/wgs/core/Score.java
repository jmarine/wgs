package org.wgs.core;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.TemporalType;

import org.wgs.entity.User;
import org.wgs.wamp.WampDict;


@Entity(name="Score")
@Table(name="APP_SCORE")
@IdClass(ScorePK.class)
public class Score implements java.io.Serializable
{
    @Id
    @ManyToOne(fetch=FetchType.LAZY)
    private LeaderBoard leaderBoard;
    

    @Id
    @Column(name="position")
    private int position;

    
    @ManyToOne(fetch= FetchType.EAGER)
    @JoinColumns({
        @JoinColumn(name="uid", referencedColumnName = "uid"),
        @JoinColumn(name="oidc_provider", referencedColumnName = "oidc_provider")
    })      
    private User   user;
    
    
    @Column(name="score_value", scale = 3, precision = 13)    
    private BigDecimal value;
    
    @javax.persistence.Temporal(TemporalType.TIMESTAMP)
    @Column(name="score_time")
    private Calendar time;
   
    
    public Score() {
        time = Calendar.getInstance();
    }
    
    /**
     * @return the leaderBoard
     */
    public LeaderBoard getLeaderBoard() {
        return leaderBoard;
    }

    /**
     * @param leaderBoard the leaderBoard to set
     */
    public void setLeaderBoard(LeaderBoard leaderBoard) {
        this.leaderBoard = leaderBoard;
    }

    /**
     * @return the position
     */
    public int getPosition() {
        return position;
    }

    /**
     * @param position the position to set
     */
    public void setPosition(int position) {
        this.position = position;
    }

    /**
     * @return the user
     */
    public User getUser() {
        return user;
    }

    /**
     * @param user the user to set
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * @return the score
     */
    public BigDecimal getValue() {
        return value;
    }

    /**
     * @param score the score to set
     */
    public void setValue(BigDecimal score) {
        this.value = score;
    }

    
    /**
     * @return the time
     */
    public java.util.Calendar getTime() {
        return time;
    }

    /**
     * @param time the time to set
     */
    public void setTime(java.util.Calendar time) {
        this.time = time;
    }
    
    
    public WampDict toWampObject() 
    {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");        
        WampDict obj = new WampDict();
        obj.put("position", getPosition());
        obj.put("name", ((getUser()!=null)? getUser().getName() : "Anonymous") );
        obj.put("picture", ((getUser()!=null)? getUser().getPicture() : "images/anonymous.png") );
        obj.put("score", getValue());
        obj.put("time", (time!=null) ? sdf.format(time) : null);
        return obj;
    }
    
}