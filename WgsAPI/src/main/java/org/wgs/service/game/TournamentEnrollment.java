package org.wgs.service.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import java.util.Calendar;
import java.util.List;
import org.wgs.wamp.type.WampDict;


@Entity
@Table(name="TOURNAMENT_ENROLLMENT")
@NamedQueries({
    @NamedQuery(name="wgs.findByTournamentAndTeam",query="SELECT OBJECT(e) FROM TournamentEnrollment e where e.tournament.id = ?1 AND e.team.id = ?2")
})
public class TournamentEnrollment 
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;    
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name="creation_date")
    private Calendar creationDate;
    
    @ManyToOne
    @JoinColumn(name="tournament")
    private Tournament tournament;
    
    @OneToOne
    private Team team;
    
    @Column(name="current_round")
    private int currentRound;
    
    @Column(name="points")
    private double points;
    
    @Column(name="loses")
    private int loses;
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "TOURNAMENT_ENROLLMENT_OPPONENT", joinColumns = @JoinColumn(name = "enrollment"), inverseJoinColumns = @JoinColumn(name = "opponent_team_id"))
    private List<Team> previousRoundsOpponents;

    @Column(name="byes")
    private int byesCount;
    
    @Column(name="previous_colors")
    private String previousColorCountsSerialized;  // or roles
    
    @Transient
    private int[] previousColorsCounts;


    
    @PostLoad
    public void convertObjects()
    {
        this.previousColorsCounts = null;
        
        String colors = getPreviousColorCountsSerialized();
        if(colors != null && colors.length() > 0) {
            String parts[] = colors.split(",");
            if(parts.length > 0) {
                int[] previousColorCounts = new int[parts.length];
                
                for(int i = 0; i < parts.length; i++) {
                    previousColorCounts[i] = Integer.parseInt(parts[i]);
                }
                
                this.setPreviousColorsCounts(previousColorCounts);
            }
        }
    }


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
     * @return the creationDate
     */
    public Calendar getCreationDate() {
        return creationDate;
    }

    /**
     * @param creationDate the creationDate to set
     */
    public void setCreationDate(Calendar creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * @return the tournament
     */
    public Tournament getTournament() {
        return tournament;
    }

    /**
     * @param tournament the tournament to set
     */
    public void setTournament(Tournament tournament) {
        this.tournament = tournament;
    }

    /**
     * @return the team
     */
    public Team getTeam() {
        return team;
    }

    /**
     * @param team the team to set
     */
    public void setTeam(Team team) {
        this.team = team;
    }

    /**
     * @return the currentRound
     */
    public int getCurrentRound() {
        return currentRound;
    }

    /**
     * @param currentRound the currentRound to set
     */
    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
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
     * @return the previousRoundsOpponents
     */
    public List<Team> getPreviousRoundsOpponents() {
        return previousRoundsOpponents;
    }

    /**
     * @param previousRoundsOpponents the previousRoundsOpponents to set
     */
    public void setPreviousRoundsOpponents(List<Team> previousRoundsOpponents) {
        this.previousRoundsOpponents = previousRoundsOpponents;
    }

    /**
     * @return the byesCount
     */
    public int getByesCount() {
        return byesCount;
    }

    /**
     * @param byesCount the byesCount to set
     */
    public void setByesCount(int byesCount) {
        this.byesCount = byesCount;
    }

    /**
     * @return the previousColorCountsSerialized
     */
    public String getPreviousColorCountsSerialized() {
        return previousColorCountsSerialized;
    }

    /**
     * @param previousColorCountsSerialized the previousColorCountsSerialized to set
     */
    public void setPreviousColorCountsSerialized(String previousColorCountsSerialized) {
        this.previousColorCountsSerialized = previousColorCountsSerialized;
    }

    /**
     * @return the previousColorsCounts
     */
    public int[] getPreviousColorsCounts() {
        return previousColorsCounts;
    }
    
    /**
     * @return the previousColorCounts
     */
    public int getPreviousColorCount(int color) {
        return (previousColorsCounts != null && color >= 0 && color < previousColorsCounts.length)? previousColorsCounts[color] : 0;
    }    

    /**
     * @param previousColorsCounts the previousColorsCounts to set
     */
    public void setPreviousColorsCounts(int[] previousColorsCounts) {
        this.previousColorsCounts = previousColorsCounts;
        StringBuilder sb = new StringBuilder();
        if(previousColorsCounts != null) {
            for(int i = 0; i < previousColorsCounts.length; i++) {
                if(i > 0) sb.append(',');
                sb.append(String.valueOf((int)previousColorsCounts[i]));
            }
        }
        this.previousColorCountsSerialized = sb.toString();
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
        WampDict retval = new WampDict();
        retval.put("id", getId());
        retval.put("created", creationDate.toInstant().toString());
        retval.put("byes", getByesCount());
        retval.put("round", getCurrentRound());
        retval.put("points", getPoints());
        retval.put("tournamentId", getTournament().getId());
        retval.put("teamId", getTeam().getId());
        retval.put("teamName", getTeam().getAlias());
        return retval;
    }

}
