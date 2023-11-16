package dictionary.base;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

import dictionary.base.algorithm.trie.Trie;
import dictionary.base.database.DictionaryDatabase;
import edu.cmu.sphinx.fst.utils.Pair;

public class Dictionary {
    private final DictionaryDatabase database;
    private final Trie words;

    public Dictionary() throws IOException, SQLException, URISyntaxException {
        words = new Trie();
        database = new DictionaryDatabase();
        for (ArrayList<String> word : getDatabase().getAllWords()) {
            words.insert(word.get(0), word.get(1));
        }
    }

    /**
     * Adds a word to the dictionary.
     *
     * @param word The Word object to add.
     */
    public void add(Word word) {
        words.insert(word.getWord(), word.getWordID());

        try {
            database.addWord(word);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Removes a word from the dictionary.
     *
     * @param word The Word object to remove.
     */
    public void remove(Word word) {
        words.remove(word.getWord());

        try {
            database.removeWord(word.getWordID());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves a list of all words in the dictionary.
     *
     * @return An ArrayList of all words in the dictionary.
     */
    public ArrayList<ArrayList<String>> getAllWords() {
        return lookup("");
    }

    /**
     * Looks up words in the dictionary with a given prefix.
     *
     * @param lookupWord The prefix to search for.
     * @return An ArrayList of words that match the given prefix.
     */
    public ArrayList<ArrayList<String>> lookup(final String lookupWord) {
        return words.getAllWordsStartWith(lookupWord);
    }

    /**
     * Removes a word from the dictionary.
     *
     * @param word The Word object to remove.
     */
    public void removeWord(final String word) {
        words.remove(word);
    }

    public DictionaryDatabase getDatabase() {
        return database;
    }

    /*public static void main(String[] args) {
        try{
            Dictionary dictionary = new Dictionary();
            System.out.println(dictionary.getAllWords().get(0).get(1));
        }catch (Exception e){

        }
    }*/
}
