package org.wgs.service.game;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import org.wgs.util.Storage;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;

@Entity
@Table(name="TOURNAMENT_MATCH")
@NamedQueries({
    @NamedQuery(name="wgs.findTournamentMatchByGID",query="SELECT OBJECT(m) FROM TournamentMatch m,IN(m.GIDs) gid WHERE gid = ?1"),
})
public class TournamentMatch 
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    
    @ManyToOne
    @JoinColumns({
        @JoinColumn(name="tournament", referencedColumnName = "tournament_id"),
        @JoinColumn(name="current_round", referencedColumnName = "current_round")
    }) 
    private TournamentRound round;
    
    @Column(name="positionInRound")
    private int positionOrderInRound;
    
   
    @OneToMany(targetEntity = TournamentMatchParticipantAndResult.class, mappedBy = "match")
    @OrderColumn(name = "positionOfTeams")      
    private List<TournamentMatchParticipantAndResult> teamsParticipantsWithResults;
    
    
    @ElementCollection
    @CollectionTable(name = "TOURNAMENT_MATCH_GROUP", joinColumns = @JoinColumn(name = "match_id"))
    @OrderColumn(name = "order_gid")
    private List<String> GIDs;
    
    @Enumerated(EnumType.ORDINAL)
    private GroupStatus status;
    
    @Column(name="isbye")
    private boolean isbye;

    
/*  @OneToOne
    @JoinColumn(name="winner", nullable = true)
    private TournamentEnrollment winner;
*/    

    
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
     * @return the round
     */
    public TournamentRound getRound() {
        return round;
    }

    /**
     * @param round the round to set
     */
    public void setRound(TournamentRound round) {
        this.round = round;
    }

    /**
     * @return the positionOrderInRound
     */
    public int getPositionOrderInRound() {
        return positionOrderInRound;
    }

    /**
     * @param positionOrderInRound the positionOrderInRound to set
     */
    public void setPositionOrderInRound(int positionOrderInRound) {
        this.positionOrderInRound = positionOrderInRound;
    }

    /**
     * @return the teamsParticipantsWithResults
     */
    public List<TournamentMatchParticipantAndResult> getTeamsParticipantsWithResults() {
        return teamsParticipantsWithResults;
    }

    /**
     * @param teamsParticipantsWithResults the teamsParticipantsWithResults to set
     */
    public void setTeamsParticipantsWithResults(List<TournamentMatchParticipantAndResult> teamsParticipantsWithResults) {
        this.teamsParticipantsWithResults = teamsParticipantsWithResults;
    }

   
    
    /**
     * @return the status
     */
    public GroupStatus getStatus() {
        return status;
    }

    /**
     * @param status the status to set
     */
    public void setStatus(GroupStatus status) {
        this.status = status;
    }    
    
    
    /**
     * @return the GIDs
     */
    public List<String> getGIDs() {
        return GIDs;
    }

    /**
     * @param GIDs the GIDs to set
     */
    public void setGIDs(List<String> GIDs) {
        this.GIDs = GIDs;
    }
    


    /**
     * @return the isbye
     */
    public boolean isBye() {
        return isbye;
    }

    /**
     * @param isbye the isbye to set
     */
    public void setBye(boolean isbye) {
        this.isbye = isbye;
    }
    
    
    
    public WampDict toWampObject()
    {
        List<String> GIDs = getGIDs();

        WampDict retval = new WampDict();
        retval.put("status", getStatus().toString());
        retval.put("gids", GIDs);

        Group group = null;
        if(GIDs != null && !GIDs.isEmpty()) {
            String lastGID = GIDs.get(GIDs.size()-1);
            group = Storage.findEntity(Group.class, lastGID);
            retval.put("group", group.toWampObject(false));
        }
        
        WampList teams = new WampList();
        for(TournamentMatchParticipantAndResult participant : getTeamsParticipantsWithResults()) {
            teams.add(participant.toWampObject());
        }
        retval.put("teams", teams);
        
        return retval;
    }


    
}

