/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bragi;

/**
 *
 * @author Sam
 */
public class Word {
    public String value;
    public String[] rhymeSounds;
    public int syllables;
    
    public static Word END_TERMINATOR = new Word("THISISANENDTERMINATOR");
    public static Word START_TERMINATOR = new Word("THISISASTARTTERMINATOR");
    
    public Word(String value){
        this.value = value;
    }
}
