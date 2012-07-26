/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.jmarine.wampservices.wsg;

import org.json.JSONObject;

/**
 *
 * @author jordi
 */
public class GroupMember
{
    private Client client;
    private String userType;
    private String nick;
    private Role   role;
    private int    team;

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
    public String getNick() {
        return nick;
    }

    /**
     * @param user the user to set
     */
    public void setNick(String nick) {
        this.nick = nick;
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
    
    
    public JSONObject toJSON() throws Exception
    {
        JSONObject obj = new JSONObject();
        obj.put("cid", ((client!=null)? client.getClientId():""));
        obj.put("nick", ((nick!=null)? nick : "") );
        obj.put("type",userType);
        obj.put("role",((role!=null)? role.getName():""));
        obj.put("team",team);
        return obj;
    }
    
    
}