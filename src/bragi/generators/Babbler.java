/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bragi.generators;

import bragi.Bragibase;
import bragi.Word;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 *
 * @author Sam
 */
public class Babbler {
    
    Bragibase bbase;
    Random rand = new Random();
    
    public Babbler(Bragibase bbase){
        this.bbase = bbase;
    }
    
    public Word[] babble(){
        
        ArrayList<Word> sentence = new ArrayList<>();
        
        sentence.addAll(Arrays.asList(bbase.getRandomStart()));
        
        Word[] nextOptions = bbase.getNextWords(sentence.toArray(new Word[0]));
        
        while (nextOptions != null && nextOptions.length > 0 ){
            Word nextWord = nextOptions[rand.nextInt(nextOptions.length)];
            
            if (nextWord == Word.END_TERMINATOR) break;
            
            sentence.add(nextWord);
            nextOptions = bbase.getNextWords(sentence.toArray(new Word[0]));
        }
        
        return sentence.toArray(new Word[0]);
    }
    
    //TODO : Maybe use ArrayLists more often instead of Arrays to minimize converions?
}
