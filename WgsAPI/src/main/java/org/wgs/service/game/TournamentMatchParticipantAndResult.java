package org.wgs.service.game;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.wgs.security.User;
import java.sql.Date;
import java.util.List;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;

@Entity
@Table(name="TOURNAMENT_MATCH_PARTICIPANT_WITH_RESULT")
@NamedQueries({
    @NamedQuery(name="wgs.findTournamentMatchResultByGID",query="SELECT OBJECT(mpr) FROM TournamentMatchParticipantAndResult mpr,IN(mpr.match.GIDs) gid WHERE gid = ?1"),
})
public class TournamentMatchParticipantAndResult 
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    
    @ManyToOne
    private TournamentMatch match;

    @Column(name="positionOfTeams")
    private int positionOfTeams;
    
    @OneToOne
    private TournamentEnrollment enrollment;

    @Column(name="result", nullable = true)
    private String result;
    
    @Column(name="points", nullable = true)
    private double points;
    
    @Column(name="wins", nullable = true)
    private int wins;    

    @Column(name="draws", nullable = true)
    private int draws;    

    @Column(name="loses", nullable = true)
    private int loses;    

    
    
    /**
     * @return the id
     */
    public long getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(long id) {
        this.id = id;
    }    

    /**
     * @return the match
     */
    public TournamentMatch getMatch() {
        return match;
    }

    /**
     * @param match the match to set
     */
    public void setMatch(TournamentMatch match) {
        this.match = match;
    }

    /**
     * @return the positionOfTeams
     */
    public int getPositionOfTeams() {
        return positionOfTeams;
    }

    /**
     * @param positionOfTeams the positionOfTeams to set
     */
    public void setPositionOfTeams(int positionOfTeams) {
        this.positionOfTeams = positionOfTeams;
    }

    /**
     * @return the enrollment
     */
    public TournamentEnrollment getEnrollment() {
        return enrollment;
    }

    /**
     * @param enrollment the enrollment to set
     */
    public void setEnrollment(TournamentEnrollment enrollment) {
        this.enrollment = enrollment;
    }

    /**
     * @return the result
     */
    public String getResult() {
        return result;
    }

    /**
     * @param result the result to set
     */
    public void setResult(String result) {
        this.result = result;
    }

    /**
     * @return the points
     */
    public double getPoints() {
        return points;
    }

    /**
     * @param points the points to set
     */
    public void setPoints(double points) {
        this.points = points;
    }

    
    /**
     * @return the wins
     */
    public int getWins() {
        return wins;
    }

    /**
     * @param wins the wins to set
     */
    public void setWins(int wins) {
        this.wins = wins;
    }

    /**
     * @return the draws
     */
    public int getDraws() {
        return draws;
    }

    /**
     * @param draws the draws to set
     */
    public void setDraws(int draws) {
        this.draws = draws;
    }

    /**
     * @return the loses
     */
    public int getLoses() {
        return loses;
    }

    /**
     * @param loses the loses to set
     */
    public void setLoses(int loses) {
        this.loses = loses;
    }
    
    
    public WampDict toWampObject()
    {
        WampDict retval = getEnrollment().getTeam().toWampObject();
        retval.put("result", getResult());
        retval.put("points", getPoints()); 
        retval.put("wins", getWins());
        retval.put("draws", getDraws());
        retval.put("loses", getLoses());
        return retval;
    }
    
}

