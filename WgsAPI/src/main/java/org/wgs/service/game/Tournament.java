package org.wgs.service.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.wgs.security.User;
import java.util.Calendar;
import java.util.List;
import org.wgs.wamp.type.WampDict;

@Entity
@Table(name="TOURNAMENT")
public class Tournament 
{
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private long id;
    
    @Column(name = "name")
    private String name;  // 0=Swiss-system, 1=knockout matches
    
    @Column(name = "type")
    private int tournamentType;  // 0=Swiss-system, 1=knockout single elimination, 2=knockout double elimination
    
    @ManyToOne(fetch=FetchType.LAZY)
    private Application application;
    
    @Column(name = "state")
    private GroupState state;  // OPEN, STARTED, FINISHED

    @ManyToOne(fetch=FetchType.LAZY)
    private User owner;
    
    @Column(name = "created")
    private Calendar created;
    
    @Column(name = "start")
    private Calendar start;
    
    @Column(name = "max_duration")
    private int maxRoundDurationInMinutes;

    @Column(name = "min_teams")
    private int minTeams = 2;
    
    @Column(name = "max_teams")
    private int maxTeams = 0;  // unbounded

    @Column(name = "current_round")
    private int currentRound = 0;

    
    @OneToMany(fetch = FetchType.LAZY, mappedBy="tournament")
    private List<TournamentEnrollment> enrollments;
    
    @OneToMany(fetch = FetchType.LAZY, mappedBy="tournament")
    private List<TournamentRound> rounds;    

    
    

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
     * @return the tournamentType
     */
    public int getTournamentType() {
        return tournamentType;
    }

    /**
     * @param tournamentType the tournamentType to set
     */
    public void setTournamentType(int tournamentType) {
        this.tournamentType = tournamentType;
    }


    /**
     * @return the state
     */
    public GroupState getState() {
        return state;
    }

    /**
     * @param state the state to set
     */
    public void setState(GroupState state) {
        this.state = state;
    }

    /**
     * @return the owner
     */
    public User getOwner() {
        return owner;
    }

    /**
     * @param owner the owner to set
     */
    public void setOwner(User owner) {
        this.owner = owner;
    }

    /**
     * @return the created
     */
    public Calendar getCreated() {
        return created;
    }

    /**
     * @param created the created to set
     */
    public void setCreated(Calendar created) {
        this.created = created;
    }

    /**
     * @return the start
     */
    public Calendar getStart() {
        return start;
    }

    /**
     * @param start the start to set
     */
    public void setStart(Calendar start) {
        this.start = start;
    }

    /**
     * @return the maxRoundDurationInMinutes
     */
    public int getMaxRoundDurationInMinutes() {
        return maxRoundDurationInMinutes;
    }

    /**
     * @param maxRoundDurationInMinutes the maxRoundDurationInMinutes to set
     */
    public void setMaxRoundDurationInMinutes(int maxRoundDurationInMinutes) {
        this.maxRoundDurationInMinutes = maxRoundDurationInMinutes;
    }
    
    

    /**
     * @return the minTeams
     */
    public int getMinTeams() {
        return minTeams;
    }

    /**
     * @param minTeams the minTeams to set
     */
    public void setMinTeams(int minTeams) {
        this.minTeams = minTeams;
    }

    /**
     * @return the maxTeams
     */
    public int getMaxTeams() {
        return maxTeams;
    }

    /**
     * @param maxTeams the maxTeams to set
     */
    public void setMaxTeams(int maxTeams) {
        this.maxTeams = maxTeams;
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
     * @return the enrollments
     */
    public List<TournamentEnrollment> getEnrollments() {
        return enrollments;
    }

    /**
     * @param enrollments the enrollments to set
     */
    public void setEnrollments(List<TournamentEnrollment> enrollments) {
        this.enrollments = enrollments;
    }


    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
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

    /**
     * @return the rounds
     */
    public List<TournamentRound> getRounds() {
        return rounds;
    }

    /**
     * @param rounds the rounds to set
     */
    public void setRounds(List<TournamentRound> rounds) {
        this.rounds = rounds;
    }
    
    
    public TournamentManager getManager()
    {
        TournamentManager manager;
        switch(getTournamentType()) {
            case 1:
                manager = new TournamentManagerKnockoutSingleElimination();
                break;
            case 2:
                manager = new TournamentManagerKnockoutDoubleElimination();
                break;
            default:
                manager = new TournamentManagerSwissSystem();
                break;
        }
        return manager;
    }
        
    
    public WampDict toWampObject()
    {
        List<TournamentEnrollment> enrolls = getEnrollments();
        
        WampDict retval = new WampDict();
        retval.put("id", id);
        retval.put("appId", getApplication().getAppId());
        retval.put("appName", getApplication().getName());
        retval.put("type", tournamentType);
        retval.put("name", getName());
        retval.put("enrolls", (enrolls != null) ? enrolls.size() : 0);
        retval.put("created", created.toInstant().toString());
        retval.put("start", start.toInstant().toString());
        retval.put("round", currentRound);
        retval.put("max_players", maxTeams);
        retval.put("min_players", minTeams);
        retval.put("max_round_duration", maxRoundDurationInMinutes);
        retval.put("owner", owner.getUid());
        retval.put("state", String.valueOf(state));
        
        return retval;
    }

    
}
