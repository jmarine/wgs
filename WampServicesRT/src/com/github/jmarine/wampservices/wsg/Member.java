
package com.github.jmarine.wampservices.wsg;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;


public class Member
{
    private Client client;
    private String userType;
    private User   user;
    private Role   role;
    private int    team;
    private MemberState state;

    
    public Member()
    {
        state = MemberState.EMPTY;
    }
    
    /**
     * @return the client
     */
    public Client getClient() {
        return client;
    }

    /**
     * @param client the client to set
     */
    public void setClient(Client client) {
        this.client = client;
    }

    /**
     * @return the usertype
     */
    public String getUserType() {
        return userType;
    }

    /**
     * @param usertype the usertype to set
     */
    public void setUserType(String userType) {
        this.userType = userType;
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
     * @return the role
     */
    public Role getRole() {
        return role;
    }

    /**
     * @param role the role to set
     */
    public void setRole(Role role) {
        this.role = role;
    }

    /**
     * @return the team
     */
    public int getTeam() {
        return team;
    }

    /**
     * @param team the team to set
     */
    public void setTeam(int team) {
        this.team = team;
    }
    
    /**
     * @return the state
     */
    public MemberState getState() {
        return state;
    }

    /**
     * @param state the state to set
     */
    public void setState(MemberState state) {
        this.state = state;
    }    
    
    
    public ObjectNode toJSON() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode obj = mapper.createObjectNode();
        obj.put("sid", ((client!=null)? client.getSessionId():""));
        obj.put("user", ((user!=null)? user.getFQid() : "") );
        obj.put("name", ((user!=null)? user.getName() : "") );
        obj.put("type",userType);
        obj.put("state",String.valueOf(state));
        obj.put("role",((role!=null)? role.getName():""));
        obj.put("team",team);
        return obj;
    }
    
    
}