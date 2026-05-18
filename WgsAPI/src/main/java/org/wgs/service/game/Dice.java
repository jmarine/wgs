package org.wgs.service.game;

import java.io.Serializable;
import java.security.SecureRandom;


public class Dice implements Serializable 
{
    private SecureRandom sr = new SecureRandom();
    private int faces;
    
    
    public Dice() 
    { 
        this(6);
    }
    
    
    public Dice(int faces) 
    {
        init(faces);
    }
    
    public void init(int faces)
    {
        this.faces = faces;
    }
    

    /**
     * @return the value
     */
    public int getValue() {
        
        return sr.nextInt(1, getFaces());
    }

    /**
     * @return the faces
     */
    public int getFaces() {
        return faces;
    }

    /**
     * @param faces the faces to set
     */
    public void setFaces(int faces) {
        this.faces = faces;
    }

        
}
