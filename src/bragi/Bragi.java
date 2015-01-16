/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bragi;

import bragi.generators.Babbler;
import bragi.test.TestWordWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Sam
 */
public class Bragi {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        Bragibase bb = new Bragibase();
        Babbler bab = new Babbler(bb);
        Wordifier wf = new Wordifier();
        

        
        try {
        
            wf.compileDictionary("cmudict/cmudict.dict");
        
            for (Word[] sentence : wf.wordifyTrainingFile("TrainingSentences.txt")){
                bb.addChain(sentence);
            }
            
            TestWordWriter tww = new TestWordWriter("outTraining.txt");
            wf.streamWordifiedContent("TrainingSentences.txt", tww);
            tww.close();
            
            tww = new TestWordWriter("outGB.txt");
            wf.streamWordifiedContent("RawTexts/Ghostbusters.txt", bb);
            tww.close();
            
            tww = new TestWordWriter("outTND.txt");
            wf.streamWordifiedContent("RawTexts/tomorrowneverdies.txt", bb);
            tww.close();
            
            tww = new TestWordWriter("outID4.txt");
            wf.streamWordifiedContent("RawTexts/ID4.txt", bb);
            tww.close(); 
            
        } catch (IOException ex) {
            Logger.getLogger(Bragi.class.getName()).log(Level.SEVERE, null, ex);
        }     
    
        for (int i=0;i<100;i++){
            System.out.println(Wordifier.compileWords(bab.babble()));
        }
        
        
        bb.graphDb.shutdown();
    }
    
}
