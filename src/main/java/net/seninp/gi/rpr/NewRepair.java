package net.seninp.gi.rpr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import net.seninp.gi.repair.RePairGrammar;
import net.seninp.gi.repair.RePairGuard;
import net.seninp.gi.repair.RePairRule;
import net.seninp.gi.repair.RePairSymbol;
import net.seninp.gi.repair.RePairSymbolRecord;

/**
 * Improved repair implementation.
 * 
 * @author psenin
 *
 */
public class NewRepair {

  private static final String SPACE = " ";

  // logging stuff
  //
  private static Logger consoleLogger;
  private static Level LOGGING_LEVEL = Level.ALL;

  static {
    consoleLogger = (Logger) LoggerFactory.getLogger(NewRepair.class);
    consoleLogger.setLevel(LOGGING_LEVEL);
  }

  /**
   * Parses the input string into a grammar.
   * 
   * @param inputStr the string to parse.
   * @return
   */
  public static RePairGrammar parse(String inputStr) {

    consoleLogger.debug(
        "input string (" + String.valueOf(countSpaces(inputStr) + 1) + " tokens): " + inputStr);

    RePairGrammar grammar = new RePairGrammar();

    // two data structures
    //
    // 1.0. - the string
    ArrayList<RePairSymbolRecord> symbolizedString = new ArrayList<RePairSymbolRecord>(
        countSpaces(inputStr) + 1);

    // 2.0. - the priority queue
    RepairPriorityQueue digramsQueue = new RepairPriorityQueue();

    // 3.0. - the R0 digrams occurrence hashtable: <digram string> -> <R0 occurrence indexes>
    HashMap<String, ArrayList<Integer>> digramsTable = new HashMap<String, ArrayList<Integer>>();
    // 3.1. - all digrams ever seen will be here
    HashMap<String, ArrayList<Integer>> allDigramsTable = new HashMap<String, ArrayList<Integer>>();

    // tokenize the input string
    StringTokenizer st = new StringTokenizer(inputStr, " ");

    // while there are tokens, populate digrams hash and construct the table
    //
    int stringPositionCounter = 0;
    while (st.hasMoreTokens()) {

      // got token, make a symbol
      String token = st.nextToken();
      RePairSymbol symbol = new RePairSymbol(token, stringPositionCounter);
      consoleLogger.debug("token @" + stringPositionCounter + ": " + token);

      // add it to the string
      RePairSymbolRecord sr = new RePairSymbolRecord(symbol);
      symbolizedString.add(sr);

      // make a digram if we at the second and all consecutive places
      if (stringPositionCounter > 0) {

        // digram str
        StringBuffer digramStr = new StringBuffer();
        digramStr.append(symbolizedString.get(stringPositionCounter - 1).getPayload().toString())
            .append(SPACE)
            .append(symbolizedString.get(stringPositionCounter).getPayload().toString());

        // fill the digram occurrence frequency
        if (digramsTable.containsKey(digramStr.toString())) {
          digramsTable.get(digramStr.toString()).add(stringPositionCounter - 1);
          consoleLogger.debug(" .added a digram entry to: " + digramStr.toString());
        }
        else {
          ArrayList<Integer> arr = new ArrayList<Integer>();
          arr.add(stringPositionCounter - 1);
          digramsTable.put(digramStr.toString(), arr);
          consoleLogger.debug(" .created a digram entry for: " + digramStr.toString());
        }

        symbolizedString.get(stringPositionCounter - 1).setNext(sr);
        sr.setPrevious(symbolizedString.get(stringPositionCounter - 1));

      }

      // go on
      stringPositionCounter++;
    }
    consoleLogger.debug("parsed the input string into the doubly linked list of tokens ...");
    consoleLogger.debug("RePair input: " + asString(symbolizedString));
    consoleLogger.debug("digrams table: " + printHash(digramsTable).replace("\n",
        "\n                                                                       "));
    allDigramsTable.putAll(digramsTable);

    consoleLogger.debug("populating the priority queue...");
    // populate the priority queue and the index -> digram record map
    //
    for (Entry<String, ArrayList<Integer>> e : digramsTable.entrySet()) {
      if (e.getValue().size() > 1) {
        // create a digram record
        RepairDigramRecord dr = new RepairDigramRecord(e.getKey(), e.getValue().size());
        // put the record into the priority queue
        digramsQueue.enqueue(dr);
      }
    }
    consoleLogger.debug(digramsQueue.toString().replace("\n",
        "\n                                                        "));

    // start the Re-Pair cycle
    //
    RepairDigramRecord entry = null;
    while ((entry = digramsQueue.dequeue()) != null) {

      consoleLogger.debug(" *the current R0: " + asString(symbolizedString));
      consoleLogger.debug(" *digrams table: " + printHash(digramsTable).replace("\n",
          "\n                                                                         "));

      consoleLogger.debug(" *polled a priority queue entry: " + entry.str + " : " + entry.freq);
      consoleLogger.debug(" *" + digramsQueue.toString().replace("\n",
          "\n                                                          "));

      // create a new rule
      //
      ArrayList<Integer> occurrences = digramsTable.get(entry.str);

      RePairSymbolRecord first = symbolizedString.get(occurrences.get(0));
      RePairSymbolRecord second = first.getNext();

      RePairRule r = new RePairRule(grammar);

      r.setFirst(first.getPayload());
      r.setSecond(second.getPayload());
      r.assignLevel();

      StringBuffer expandedRule = new StringBuffer();
      if (first.getPayload().isGuard()) {
        expandedRule.append(((RePairGuard) first.getPayload()).getRule().toExpandedRuleString());
      }
      else {
        expandedRule.append(((RePairSymbol) first.getPayload()).toString());
      }
      expandedRule.append(SPACE);
      if (second.getPayload().isGuard()) {
        expandedRule.append(((RePairGuard) second.getPayload()).getRule().toExpandedRuleString());
      }
      else {
        expandedRule.append(((RePairSymbol) second.getPayload()).toString());
      }
      r.setExpandedRule(expandedRule.toString());

      consoleLogger.debug(" .creating the rule: " + r.toInfoString());

      // substitute each digram entry with the rule
      //
      consoleLogger.debug(" .substituting the digram at locations: " + occurrences.toString());
      HashSet<String> newDigrams = new HashSet<String>(occurrences.size());
      for (Integer currentIndex : occurrences) {

        RePairSymbolRecord currentS = symbolizedString.get(currentIndex);
        RePairSymbolRecord nextS = symbolizedString.get(currentIndex).getNext();

        // 1.0. create a new guard to replace the digram
        //
        RePairGuard g = new RePairGuard(r);
        g.setStringPosition(currentS.getIndex());
        r.addOccurrence(currentS.getIndex());
        RePairSymbolRecord guard = new RePairSymbolRecord(g);
        symbolizedString.set(currentIndex, guard);
        // also place a NULL placeholder next
        RePairSymbolRecord nextNotNull = nextS.getNext();
        guard.setNext(nextNotNull);
        if (null != nextNotNull) {
          nextNotNull.setPrevious(guard);
        }
        RePairSymbolRecord prevNotNull = currentS.getPrevious();
        guard.setPrevious(prevNotNull);
        if (null != prevNotNull) {
          prevNotNull.setNext(guard);
        }

        // 2.0 correct entry at the left
        //
        if (currentIndex > 0) {

          RePairSymbolRecord prevS = currentS.getPrevious();

          if (null != prevS) {

            // cleanup old left digram
            String oldLeftDigram = prevS.getPayload().toString() + " "
                + currentS.getPayload().toString();
            int newFreq = digramsTable.get(oldLeftDigram).size() - 1;
            consoleLogger
                .debug(" .removed left digram entry @" + prevS.getPayload().getStringPosition()
                    + " " + oldLeftDigram + ", new freq: " + newFreq);
            digramsTable.get(oldLeftDigram).remove(Integer.valueOf(prevS.getIndex()));
            digramsQueue.updateDigramFrequency(oldLeftDigram, newFreq);

            // if it was the last entry...
            if (0 == newFreq) {
              digramsTable.remove(oldLeftDigram);
            }

            // and place the new digram entry
            String newLeftDigram = prevS.getPayload().toString() + " " + r.toString();
            // see the new freq..
            if (digramsTable.containsKey(newLeftDigram)) {
              digramsTable.get(newLeftDigram).add(prevS.getPayload().getStringPosition());
              consoleLogger.debug(" .added a digram entry to: " + newLeftDigram + ", @"
                  + prevS.getPayload().getStringPosition());
            }
            else {
              ArrayList<Integer> arr = new ArrayList<Integer>();
              arr.add(prevS.getPayload().getStringPosition());
              digramsTable.put(newLeftDigram, arr);
              consoleLogger.debug(" .created a digram entry for: " + newLeftDigram.toString()
                  + ", @" + prevS.getPayload().getStringPosition());
            }
            newDigrams.add(newLeftDigram);
          }
        }

        // 3.0 correct entry at the right
        //
        if (currentIndex < symbolizedString.size() - 2) {

          RePairSymbolRecord nextSS = nextS.getNext();

          if (null != nextSS) {

            // cleanup old left digram
            String oldRightDigram = nextS.getPayload().toString() + " "
                + nextSS.getPayload().toString();
            int newFreq = digramsTable.get(oldRightDigram).size() - 1;
            consoleLogger
                .debug(" .removed right digram entry @" + nextSS.getPayload().getStringPosition()
                    + " " + oldRightDigram + ", new freq: " + newFreq);
            digramsTable.get(oldRightDigram).remove(Integer.valueOf(nextS.getIndex()));
            digramsQueue.updateDigramFrequency(oldRightDigram, newFreq);

            // if it was the last entry...
            if (0 == newFreq) {
              digramsTable.remove(oldRightDigram);
            }

            // and place the new digram entry
            String newRightDigram = r.toString() + " " + nextSS.getPayload().toString();
            // see the new freq..
            if (digramsTable.containsKey(newRightDigram)) {
              digramsTable.get(newRightDigram).add(currentS.getPayload().getStringPosition());
              consoleLogger.debug(" .added a digram entry to: " + newRightDigram + ", @"
                  + currentS.getPayload().getStringPosition());
            }
            else {
              ArrayList<Integer> arr = new ArrayList<Integer>();
              arr.add(currentS.getPayload().getStringPosition());
              digramsTable.put(newRightDigram, arr);
              consoleLogger.debug(" .created a digram entry for: " + newRightDigram.toString()
                  + ", @" + currentS.getPayload().getStringPosition());
            }
            newDigrams.add(newRightDigram);
          }
        }

      } // walk over all occurrences

      // voila -- remove the digram itself from the tracking table
      digramsTable.remove(entry.str);

      // update new digram frequencies and if needed place those into priority queue
      //
      for (String digramStr : newDigrams) {
        if (digramsTable.get(digramStr).size() > 1) {
          if (digramsQueue.containsDigram(digramStr)) {
            digramsQueue.updateDigramFrequency(digramStr, digramsTable.get(digramStr).size());
          }
          else {
            digramsQueue
                .enqueue(new RepairDigramRecord(digramStr, digramsTable.get(digramStr).size()));
          }
        }
      }

    }

    consoleLogger.debug("finished RePair run ...");
    consoleLogger.debug("R0: " + asString(symbolizedString));
    consoleLogger.debug("digrams table: " + printHash(digramsTable).replace("\n",
        "\n                                                                       "));
    consoleLogger.debug("digrams queue: " + digramsQueue.toString().replace("\n",
        "\n                                                        "));

    grammar.setR0String(asString(symbolizedString));

    return grammar;

  }

  private static String printHash(HashMap<String, ArrayList<Integer>> digramsTable) {
    StringBuffer sb = new StringBuffer();
    for (Entry<String, ArrayList<Integer>> e : digramsTable.entrySet()) {
      sb.append(e.getKey()).append(" -> ").append(e.getValue().toString()).append("\n");
    }
    return sb.delete(sb.length() - 1, sb.length()).toString();
  }

  private static String asString(ArrayList<RePairSymbolRecord> symbolizedString) {
    StringBuffer res = new StringBuffer();
    RePairSymbolRecord s = symbolizedString.get(0); // since digrams are starting from left symbol,
                                                    // the symbol 0 is never NULL
    do {
      res.append(s.getPayload().toString()).append(" ");
      s = s.getNext();
    }
    while (null != s);
    return res.toString();
  }

  /**
   * Counts spaces in the string.
   * 
   * @param str the string to process.
   * @return number of spaces found.
   */
  private static int countSpaces(String str) {
    if (null == str) {
      return -1;
    }
    int counter = 0;
    for (int i = 0; i < str.length(); i++) {
      if (str.charAt(i) == ' ') {
        counter++;
      }
    }
    return counter;
  }
}
