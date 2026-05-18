package org.wgs.service.game;

import java.util.Map;



public class GroupInternalDataForDiceGame implements GroupInternalData
{
    //@Column(name = "num_dices")
    private int numDices;
    
    //@Column(name = "num_faces")
    private int numFaces;
    
    public void init(Map<String,Object> options)
    {
        setNumDices(Integer.parseInt((String)options.get("num_dices")));
        setNumFaces(Integer.parseInt((String)options.get("num_faces")));
    }
    


    /**
     * @return the numDices
     */
    public int getNumDices() {
        return numDices;
    }

    /**
     * @param numDices the numDices to set
     */
    public void setNumDices(int numDices) {
        this.numDices = numDices;
    }

    /**
     * @return the numFaces
     */
    public int getNumFaces() {
        return numFaces;
    }

    /**
     * @param numFaces the numFaces to set
     */
    public void setNumFaces(int numFaces) {
        this.numFaces = numFaces;
    }
    
}
