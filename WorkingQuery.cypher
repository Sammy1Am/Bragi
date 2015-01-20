//Random from start with syllable limit:
MATCH (a:EndNode {side:"start"})-[:FOLLOWED_BY]->(ss:State)
WITH ss,rand() as ssnumber ORDER BY ssnumber LIMIT 1
MATCH p=(a:EndNode {side:"start"})-[:FOLLOWED_BY]->(ss)-[FOLLOWED_BY*4..10]->(b:EndNode {side:"end"})
WITH p,rand() as pnumber
UNWIND NODES(p) AS np
MATCH (np)-[:WORD]->(w:Word)
WITH p,COLLECT(w.value) AS wPath,SUM(w.syllables) AS tSyl WHERE tSyl = 5
RETURN wPath,tSyl

//Random from end with syllable limit:
MATCH (a:EndNode {side:"end"})<-[:FOLLOWED_BY]-(ss:State)
WITH ss,rand() as ssnumber ORDER BY ssnumber LIMIT 1
MATCH p=(a:EndNode {side:"end"})<-[:FOLLOWED_BY]-(ss)<-[FOLLOWED_BY*4..10]-(b:EndNode {side:"start"})
WITH p,rand() as pnumber
UNWIND NODES(p) AS np
MATCH (np)-[:WORD]->(w:Word)
WITH p,COLLECT(w.value) AS wPath,SUM(w.syllables) AS tSyl WHERE tSyl >= 5
RETURN wPath,tSyl

//Random rhyme
MATCH (tw:Word {value:"blue"})-[:HAS_RHYME]->(r:RhymeSound)<-[:HAS_RHYME]-(rw:Word)<-[:WORD]-(ss:State)-[FOLLOWED_BY]->(b:EndNode {side:"end"})
WITH ss,rand() as ssnumber ORDER BY ssnumber LIMIT 20
MATCH p=(a:EndNode {side:"start"})-[:FOLLOWED_BY*4..6]->(ss)-[FOLLOWED_BY]->(b:EndNode {side:"end"})
WITH p,rand() as pnumber //ORDER BY pnumber LIMIT 1
UNWIND NODES(p) AS np
MATCH (np)-[:WORD]->(w:Word)
WITH p,COLLECT(w.value) AS wPath,SUM(w.syllables) AS tSyl WHERE tSyl >12

// Haiku parts
MATCH (a:EndNode {side:"start"})-[:FOLLOWED_BY]->(ss:State)
WITH ss,rand() as ssnumber ORDER BY ssnumber LIMIT 1
MATCH p=(a:EndNode {side:"start"})-[:FOLLOWED_BY]->(ss)-[FOLLOWED_BY*4..8]->(b:EndNode {side:"end"})
WITH p,rand() as pnumber
UNWIND NODES(p) AS np
MATCH (np)-[:WORD]->(w:Word)
WITH p,COLLECT(w) AS wPath,SUM(w.syllables) AS tSyl WHERE tSyl IN [5,12,17]
RETURN wPath,tSyl