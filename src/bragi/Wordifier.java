/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bragi;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Sam
 */
public class Wordifier {

    HashMap<String, ArrayList<Object[]>> dictionary = new HashMap<>();
    private final Pattern SENTENCE_SPLIT_PAT = Pattern.compile("\\W*(\\s+|$)\\W?");
    private final Pattern DICT_PAT = Pattern.compile("(['\\w]+)(\\(\\d\\))? ([A-Z\\d ]+)");

    /**
     * Converts a single sentence (between start and end terminators) into a
     * word array
     *
     * @param sentence
     * @return
     */
    public Word[] wordifySentence(String sentence) {
        String cleanedSentence = sentence.trim();

        String[] splitWords = SENTENCE_SPLIT_PAT.split(cleanedSentence);

        ArrayList<Word> resultWords = new ArrayList<>();

        for (String s : splitWords) {
            resultWords.add(createWord(s));
        }

        return resultWords.toArray(new Word[resultWords.size()]);
    }

    public Word[][] wordifyTrainingFile(String filePath) throws IOException {
        ArrayList<Word[]> sentenceArray = new ArrayList<>();
        try (BufferedReader inputFile = new BufferedReader(new FileReader(filePath))) {

            for (String nextLine = inputFile.readLine(); nextLine != null; nextLine = inputFile.readLine()) {
                sentenceArray.add(wordifySentence(nextLine));
            }
        }
        return sentenceArray.toArray(new Word[0][0]);
    }

    public void streamWordifiedContent(String filePath, IWordConsumer wrdCon) throws IOException {
        //TODO: Make this take a generic method / interface instead of Bragibase incase additional processing is needed

        try (BufferedReader inputFile = new BufferedReader(new FileReader(filePath))) {

            System.out.println(String.format("Streaming words from %1s", filePath));

            StringBuilder rollingBuilder = new StringBuilder();
            boolean forceMatch = false;

            Pattern sentencePattern = Pattern.compile("((Dr\\.|Mrs\\.|Mr\\.|Ms\\.|[^\\.\\?\\!])+[\\.\\?\\!]).*", Pattern.CASE_INSENSITIVE);
            Pattern capitalsLine = Pattern.compile("[A-Z\\s\\W]+");
            Matcher sentenceMatcher;

            long lineNum = 0;

            for (String nextLine = inputFile.readLine(); nextLine != null; nextLine = inputFile.readLine()) {
                lineNum++;

                if (lineNum % 500 == 0) {
                    System.out.println(String.format("Processing line %1s", lineNum));
                }

                if (nextLine.trim().isEmpty() || capitalsLine.matcher(nextLine).matches()) { // A whole blank line or just capitals
                    if (rollingBuilder.length() > 0) {
                        // Skip any extra bits
                        rollingBuilder = new StringBuilder(); //TODO Maybe use a buffer to make this easier?
                        //forceMatch = true; // Force output of current info
                    }
                } else {
                    rollingBuilder.append(" "); // Add whitespace to keep things separated
                    rollingBuilder.append(nextLine);
                }

                if (forceMatch) {
                    wrdCon.addChain(wordifySentence(rollingBuilder.toString()));
                    rollingBuilder = new StringBuilder(); //TODO Maybe use a buffer to make this easier?
                    forceMatch = false;
                    continue;
                }

                sentenceMatcher = sentencePattern.matcher(rollingBuilder);

                while (sentenceMatcher.matches()) {

                    // Remove parentheticals
                    String matchedString = sentenceMatcher.group(1).replaceAll("\\(.*\\)", "");

                    Word[] newWords = wordifySentence(matchedString);
                    if (newWords.length > 2) { // Only worry about sentences with at least 3 words
                        wrdCon.addChain(newWords);
                    }
                    rollingBuilder.delete(0, sentenceMatcher.group(1).length());
                    sentenceMatcher = sentencePattern.matcher(rollingBuilder); // TODO There's gotta be a better way of doing this...
                }
            }
        }
    }

    /**
     * Converts an array of words back into a string.
     *
     * @param words
     * @return
     */
    public static String compileWords(Word[] words) {
        StringBuilder newString = new StringBuilder();

        for (int w = 0; w < words.length; w++) {
            if (w > 0) {
                newString.append(" ");
            }
            newString.append(words[w].value); // TODO Figure out why some words come back null on rhymes?
        }

        return newString.toString();
    }

    public void compileDictionary(String dictionaryFilePath) throws IOException {
        try (BufferedReader inputFile = new BufferedReader(new FileReader(dictionaryFilePath))) {
            Matcher lineMatcher;
            for (String nextLine = inputFile.readLine(); nextLine != null; nextLine = inputFile.readLine()) {
                lineMatcher = DICT_PAT.matcher(nextLine);
                if (lineMatcher.matches()) {
                    String word = lineMatcher.group(1);
                    String[] phones = lineMatcher.group(3).split(" ");

                    int syllables = 0;
                    String rhymeSound;
                    int rhymeStartIndex = 0;
                    int rhymeAccentLevel = -1;

                    for (int p = phones.length - 1; p >= 0; p--) {
                        if (phones[p].length() > 2) {
                            syllables++; // It's a vowel in the CMUDICT

                            int newLevel = Integer.parseInt(phones[p].substring(2));

                            if (rhymeAccentLevel < 1) { // If the current accent is non-existant or 0, go ahead and treat this as a better option.
                                rhymeAccentLevel = newLevel;
                                rhymeStartIndex = p;
                            }
                        }
                    }

                    StringBuilder sb = new StringBuilder();
                    for (int r = rhymeStartIndex; r < phones.length; r++) {
                        sb.append(phones[r]);
                    }
                    rhymeSound = sb.toString();

                    if (!dictionary.containsKey(word)) {
                        dictionary.put(word, new ArrayList<Object[]>());
                    }

                    dictionary.get(word).add(new Object[]{syllables, rhymeSound});
                }
            }
        }
    }

    private Word createWord(String inputString) {
        
        /*
            Lowercase, and remove weird punctuation, but not commas or apostrophes since they might be part of the word
        */
        Word newWord = new Word(inputString.toLowerCase().replaceAll("[\\\"\\(\\)]", ""));

        // If anything is hyphenated, just look-up the rhyme for the last word, and add syllables for both.
        String[] subwords = newWord.value.split("-");

        int syllables = 0;
        
        for (int s = 0; s < subwords.length; s++) {
            if (dictionary.containsKey(subwords[s])) {
                
                ArrayList<String> rhymeSounds = new ArrayList<>();
                ArrayList<Object[]> dicDefs = dictionary.get(subwords[s]);
                
                syllables += (int)dicDefs.get(0)[0]; // Add syllables just from first option
                
                for (Object[] info : dicDefs) {
                    if (s == subwords.length-1) rhymeSounds.add((String) info[1]);// Only do rhyme sounds for the last word among sub-words
                }
                newWord.rhymeSounds = rhymeSounds.toArray(new String[rhymeSounds.size()]);
            }
        }
        
        newWord.syllables = syllables;

        return newWord;
    }
}
