package org.wampservices.wgs;


public class LeaderBoardPK implements java.io.Serializable
{
    private String application;
    private int id;
    
    @Override
    public boolean equals(Object o) { 
        if( (o != null) && (o instanceof LeaderBoardPK) ) {
            LeaderBoardPK pk = (LeaderBoardPK)o;
            return application.equals(pk.application) && (id == pk.id);
        } else {
            return false;
        }
    }
    
    
    @Override
    public int hashCode() {
        return application.hashCode() + id;
    }
        
}
