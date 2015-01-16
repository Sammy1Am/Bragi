/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bragi.test;

import bragi.IWordConsumer;
import bragi.Word;
import bragi.Wordifier;
import java.io.IOException;
import java.io.PrintWriter;

/**
 *
 * @author Sam
 */
public class TestWordWriter implements IWordConsumer{
    
    PrintWriter fileOut;
    
    public TestWordWriter(String filePath) throws IOException{
        fileOut = new PrintWriter(filePath);
    }
    
    public void close() throws IOException{
        fileOut.close();
    }

    @Override
    public void addChain(Word[] words) {
        fileOut.println(Wordifier.compileWords(words));
    }
    
}
