package org.wgs.core;


public class ScorePK implements java.io.Serializable
{
    private LeaderBoardPK leaderBoard;
    private int position;
    
    @Override
    public boolean equals(Object o) { 
        if( (o != null) && (o instanceof LeaderBoardPK) ) {
            ScorePK pk = (ScorePK)o;
            return leaderBoard.equals(pk.leaderBoard) && (position == pk.position);
        } else {
            return false;
        }
    }
    
    
    @Override
    public int hashCode() {
        return leaderBoard.hashCode() + position;
    }
        
}
