/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bragi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.neo4j.cypher.ExecutionEngine;
import org.neo4j.cypher.ExecutionResult;
import org.neo4j.graphdb.ConstraintViolationException;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.kernel.api.exceptions.schema.AlreadyIndexedException;
import org.neo4j.kernel.impl.util.StringLogger;

/**
 *
 * @author Sam
 */
public class Bragibase implements IWordConsumer {

    GraphDatabaseService graphDb;
    ExecutionEngine engine;

    private final String DB_PATH = "bbase";
    private final int DB_ORDER = 2;

    private static enum RelTypes implements RelationshipType {
        FOLLOWED_BY, HAS_RHYME
    }

    private static final String[] FOLLOW_NAMES = new String[]{"zero", "one", "two", "three", "four", "five"};
    private static final String WORD_VALUE = "value";
    private static final String SYLLABLE_VALUE = "syllables";

    private static final String RHYME_VALUE = "value";
    
    Label terminalLabel = DynamicLabel.label("EndNode");
    Label wordLabel = DynamicLabel.label("Word");
    Label rhymeLabel = DynamicLabel.label("RhymeSound");

    public final Node startNode;
    public final Node endNode;

    public Bragibase() {
        graphDb = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(DB_PATH)
                .setConfig(GraphDatabaseSettings.relationship_keys_indexable, "one,two")
                .setConfig(GraphDatabaseSettings.relationship_auto_indexing, "true")
                .newGraphDatabase();
        registerShutdownHook(graphDb);
        engine = new ExecutionEngine(graphDb, StringLogger.SYSTEM);

        setupIndexes();
        
        try (Transaction tx = graphDb.beginTx()) {

            Node gotStartNode = getFirstMatch(terminalLabel, "side", "start");

            if (gotStartNode == null) {
                gotStartNode = graphDb.createNode(terminalLabel);
                gotStartNode.setProperty("side", "start");
            }

            startNode = gotStartNode;

            Node gotEndNode = getFirstMatch(terminalLabel, "side", "end");

            if (gotEndNode == null) {
                gotEndNode = graphDb.createNode(terminalLabel);
                gotEndNode.setProperty("side", "end");
            }

            endNode = gotEndNode;

            tx.success();
        }
    }

    private void setupIndexes(){
        try (Transaction tx = graphDb.beginTx()) {

            Schema schema = graphDb.schema();
            

            try {
                schema.indexFor(rhymeLabel).on(RHYME_VALUE).create();
                schema.constraintFor(terminalLabel).assertPropertyIsUnique("side").create();
                schema.constraintFor(wordLabel).assertPropertyIsUnique(WORD_VALUE).create();
            } catch (ConstraintViolationException ex) {
                // No worries
            }
            
            tx.success();
        }
    }
    
    private Node getFirstMatch(Label label, String property, Object value) {
        try (ResourceIterator<Node> matches
                = graphDb.findNodesByLabelAndProperty(label, property, value).iterator()) {
            if (matches.hasNext()) {
                Node matched = matches.next();
                matches.close();
                return matched;
            }
        }
        return null;
        //TODO Should be able to use a getOrCreate method instead of calling this first... can't figure it out
    }

    private Node getOrCreateWord(Word word) {
        Node returnNode;

        returnNode = getFirstMatch(wordLabel, WORD_VALUE, word.value);

        if (returnNode == null) {
            returnNode = graphDb.createNode(wordLabel);
            returnNode.setProperty(WORD_VALUE, word.value);
            returnNode.setProperty(SYLLABLE_VALUE, word.syllables);
            
            if (word.rhymeSounds != null){ //TODO, hopefully with algorithmic pronunciation, this won't be an issue
                for (String rhymeSound : word.rhymeSounds){
                    Node rhymeNode = getOrCreateRhymeSound(rhymeSound);
                    returnNode.createRelationshipTo(rhymeNode, RelTypes.HAS_RHYME);
                }
            }
        }

        return returnNode;
    }
    
    private Node getOrCreateRhymeSound(String rhymeSound){
        Node returnNode;
        
        returnNode = getFirstMatch(rhymeLabel, RHYME_VALUE, rhymeSound);
        
        if (returnNode == null) {
            returnNode = graphDb.createNode(rhymeLabel);
            returnNode.setProperty(RHYME_VALUE, rhymeSound);
        }
        
        return returnNode;
    }

    @Override
    public void addChain(Word[] chainToAdd) {

        try (Transaction tx = graphDb.beginTx()) {
            Node[] wordNodes = new Node[chainToAdd.length + 2];
            wordNodes[0] = startNode;
            wordNodes[wordNodes.length - 1] = endNode;

            // Populate word nodes from chain
            for (int w = 1; w <= chainToAdd.length; w++) {
                wordNodes[w] = getOrCreateWord(chainToAdd[w - 1]);
            }

            // For each grop of DB_ORDER+1 words, create a relationship.  For-loop
            // over the target so that inputs of less than DB_ORDER will not cause
            // issues (though their nodes will be created/updated
            for (int t = DB_ORDER; t < wordNodes.length; t++) {
                //TODO Check for existing relationship

                int w = t - DB_ORDER; //Index of first node in relationship

                /*
                 Check to see if the relationship already exists by looking through each existing relationship that
                 connects these two nodes to see if the properties match the properties we WOULD create.  (i.e. the
                 intervening words).  If we find one that DOES match, set the relExists flag to true, and skip relationship
                 creation later.
                 */
                boolean relExists = false;

                for (Relationship r : wordNodes[t].getRelationships(Direction.INCOMING, RelTypes.FOLLOWED_BY)) {
                    boolean noMismatches = false;
                    if (r.getStartNode().equals(wordNodes[w])) {
                        noMismatches = true; // Set this to true knowing that we'll always have properties to AND it back to false
                        for (int n = 1; n < DB_ORDER; n++) {
                            noMismatches &= r.getProperty(FOLLOW_NAMES[n]).equals(wordNodes[w + n].getProperty(WORD_VALUE));
                        }
                    }
                    if (noMismatches) {
                        relExists = true;
                        break;
                    }
                }

                // Only create the relationship if it doesn't exist already
                if (!relExists) {
                    Relationship rel = wordNodes[t - DB_ORDER].createRelationshipTo(wordNodes[t], RelTypes.FOLLOWED_BY);

                    for (int n = 1; n < DB_ORDER; n++) {
                        rel.setProperty(FOLLOW_NAMES[n], wordNodes[w + n].getProperty(WORD_VALUE));
                    }
                }
            }
            tx.success();
        }

        //TODO While adding chain, if no new nodes were created, don't need to update any frequencies
        // on the relationships or words.  If at least one new node is being created, update frequencies for
        // all words and relationships
    }

    public ExecutionResult executeQuery(String query, Map<String, Object> params) {
        return engine.execute(query, params);
    }

    public Word[] getRandomStart() {
        Word[] returnWords = new Word[DB_ORDER];
        try (Transaction tx = graphDb.beginTx()){
            
            ResourceIterator<Map<String,Object>> results = engine.execute("MATCH (start:EndNode {side:\"start\"})-[rel:FOLLOWED_BY]->(one:Word) WITH one,rel,rand() AS number RETURN rel,one ORDER BY number LIMIT 1").javaIterator();

            if (results.hasNext()) {
                Map<String,Object> row = results.next();
                Relationship rel = (Relationship) row.get("rel");
                Node targetNode = (Node) row.get("one");
                
                for (int n=1;n<DB_ORDER;n++){
                    returnWords[n-1] = new Word(rel.getProperty(FOLLOW_NAMES[n]).toString());
                }
                
                returnWords[DB_ORDER-1] = new Word(targetNode.getProperty(WORD_VALUE).toString());
            }

            tx.success();
        }

        return returnWords;
    }
    
    
    // TODO: Alternatvely, accept the RhymeSound and look that up instead.  That'll
    // probably be a lot faster.  Maaybe?  -- still have to actually look up node, then apply
    // value to relationships...(Make sure indexes are on) 
    public Word[] getRhymingEnd(Word lastword) {
        
                Word[] returnWords = new Word[DB_ORDER];
                
        try (Transaction tx = graphDb.beginTx()){
            
            Map<String, Object> params = new HashMap<>();
            params.put("targetword", lastword.value);
            
            ResourceIterator<Map<String,Object>> results = engine.execute("MATCH (prev:Word)-[rel:FOLLOWED_BY]->(end:EndNode {side:\"end\"}), " +
                                                    "(t:Word {value:{targetword}})-[:HAS_RHYME]->(r)<-[:HAS_RHYME]-(rword:Word) " +
                                                    "WHERE rel.one = rword.value " +
                                                    "WITH prev,rel,rand() AS number RETURN prev,rel ORDER BY number LIMIT 1",params).javaIterator();

            if (results.hasNext()) {
                Map<String,Object> row = results.next();
                Relationship rel = (Relationship) row.get("rel");
                Node targetNode = (Node) row.get("prev");
                
                for (int n=1;n<DB_ORDER;n++){
                    returnWords[n] = new Word(rel.getProperty(FOLLOW_NAMES[n]).toString());
                }
                
                returnWords[0] = new Word(targetNode.getProperty(WORD_VALUE).toString());
            }

            tx.success();
        }

        return returnWords;
    }

    public Word[] getNextWords(Word[] currentState) {
        ArrayList<Word> resultWords = new ArrayList<>();

        Map<String, Object> params = new HashMap<>();

        String nextWordsQuery = createNextWordsQuery(currentState, params);

        try (Transaction tx = graphDb.beginTx();
                ResourceIterator<Node> results = engine.execute(nextWordsQuery, params).javaColumnAs("next")) {

            while (results.hasNext()) {
                Node nextNode = results.next();

                if (nextNode.hasLabel(terminalLabel)) {
                    resultWords.add(Word.END_TERMINATOR); //TODO: Account for hitting a start terminator?  Maybe?
                } else {
                    resultWords.add(new Word(nextNode.getProperty(WORD_VALUE).toString()));
                }
            }
            tx.success();
        }

        return resultWords.toArray(new Word[resultWords.size()]);
    }

    private String createNextWordsQuery(Word[] currentState, Map<String, Object> params) {

        if (currentState.length < DB_ORDER){
            throw new IllegalArgumentException(String.format("Cannot create query without at least %1s words.",DB_ORDER));
        }
        
        params.put("startword", currentState[currentState.length - DB_ORDER].value);
        
        for (int n=1;n<DB_ORDER;n++){
            params.put(FOLLOW_NAMES[n], currentState[(currentState.length - DB_ORDER)+n].value);
        }
        
        switch (Math.min(DB_ORDER, currentState.length)) {
            case 4:
                return "MATCH (start:Word {value:{startword}})-[:FOLLOWED_BY {one:{one},two:{two},three:{three}}]->(next) RETURN next";
            case 3:
                return "MATCH (start:Word {value:{startword}})-[:FOLLOWED_BY {one:{one},two:{two}}]->(next) RETURN next";
            case 2:
                return "MATCH (start:Word {value:{startword}})-[:FOLLOWED_BY {one:{one}}]->(next) RETURN next";
            case 1:
            default:
                return "MATCH (start:Word {value:{startword}})-[:FOLLOWED_BY]->(next) RETURN next";
        }
    }

    private static void registerShutdownHook(final GraphDatabaseService graphDb) {
        // Registers a shutdown hook for the Neo4j instance so that it
        // shuts down nicely when the VM exits (even if you "Ctrl-C" the
        // running application).
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                graphDb.shutdown();
            }
        });
    }
}
