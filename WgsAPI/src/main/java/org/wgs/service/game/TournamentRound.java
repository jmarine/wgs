package org.wgs.service.game;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.Calendar;
import java.util.List;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


@Entity
@Table(name="TOURNAMENT_ROUND")
public class TournamentRound 
{
    @Id
    @ManyToOne
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;
    
    @Id
    @Column(name="current_round")
    private int currentRound;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name="start_date")
    private Calendar startDate;
    
    @OneToMany(mappedBy = "round", fetch=FetchType.EAGER, cascade = { CascadeType.ALL })
    @OrderColumn(name="position")    
    private List<TournamentMatch> matches;
    
    
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
     * @return the startDate
     */
    public Calendar getStartDate() {
        return startDate;
    }

    /**
     * @param startDate the startDate to set
     */
    public void setStartDate(Calendar startDate) {
        this.startDate = startDate;
    }

    /**
     * @return the matches
     */
    public List<TournamentMatch> getMatches() {
        return matches;
    }

    /**
     * @param matches the matches to set
     */
    public void setMatches(List<TournamentMatch> matches) {
        this.matches = matches;
    }    
    

    public WampDict toWampObject()
    {
        WampDict retval = new WampDict();
        retval.put("round", getCurrentRound());
        retval.put("start", startDate.toInstant().toString());
        
        WampList matches = new WampList();
        for(TournamentMatch match : getMatches()) {
            matches.add(match.toWampObject());
        }
        retval.put("matches", matches);
        
        return retval;
    }
    
}


        