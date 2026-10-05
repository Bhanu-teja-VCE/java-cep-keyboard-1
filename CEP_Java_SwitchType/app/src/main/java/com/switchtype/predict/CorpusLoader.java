package com.switchtype.predict;

import com.switchtype.exception.CorpusFormatException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Reads text files and trains the language model and the character frequencies.
 * Lines starting with '#' are comments. Sentences end at . ? ! or the end of a line.
 */
public final class CorpusLoader {

    public static final String CORPUS_RESOURCE = "/text/corpus.txt";
    public static final String PHRASES_RESOURCE = "/text/phrases.txt";

    /** Characters that separate words (Unit III: StringTokenizer delimiters). */
    private static final String WORD_DELIMITERS = " \t\r\n,;:\"()";

    private CorpusLoader() {
    }

    /** Reads the non-comment, non-empty lines of a bundled text resource. */
    public static List<String> readResourceLines(String resource) throws CorpusFormatException {
        InputStream in = CorpusLoader.class.getResourceAsStream(resource);
        if (in == null) {
            throw new CorpusFormatException("Resource " + resource + " is missing from the program");
        }
        return readLines(new InputStreamReader(in, StandardCharsets.UTF_8), resource);
    }

    /** Reads the lines of a corpus file from disk (Unit III: FileInputStream). */
    public static List<String> readFileLines(File file) throws CorpusFormatException {
        try {
            return readLines(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8), file.getName());
        } catch (IOException e) {
            throw new CorpusFormatException("Cannot open " + file.getName() + ": " + e.getMessage(), e);
        }
    }

    private static List<String> readLines(Reader source, String name) throws CorpusFormatException {
        List<String> lines = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(source);
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.indexOf('\0') >= 0) {
                    throw new CorpusFormatException(name + " looks like a binary file, not text");
                }
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    lines.add(trimmed.toLowerCase());
                }
            }
        } catch (IOException e) {
            throw new CorpusFormatException("Cannot read " + name + ": " + e.getMessage(), e);
        } finally {
            try {
                reader.close();                          // Unit II: finally releases the file
            } catch (IOException ignored) {
                // closing a reader we only read from cannot lose data
            }
        }
        if (lines.isEmpty()) {
            throw new CorpusFormatException(name + " contains no text");
        }
        return lines;
    }

    /** Trains the model with every sentence of every line. */
    public static void train(LanguageModel model, List<String> lines) {
        for (String line : lines) {
            for (String sentence : line.split("[.?!]")) {
                String previous = WordPredictor.START;
                StringTokenizer tokens = new StringTokenizer(sentence, WORD_DELIMITERS);
                while (tokens.hasMoreTokens()) {
                    String word = clean(tokens.nextToken());
                    if (!word.isEmpty()) {
                        model.add(previous, word);
                        previous = word;
                    }
                }
            }
        }
    }

    /** Keeps only the letters and digits of a token, lower case. */
    public static String clean(String token) {
        StringBuilder sb = new StringBuilder();
        for (char c : token.toLowerCase().toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** All distinct words of the lines (Unit III: HashSet). */
    public static HashSet<String> distinctWords(List<String> lines) {
        HashSet<String> words = new HashSet<String>();
        for (String line : lines) {
            StringTokenizer tokens = new StringTokenizer(line, WORD_DELIMITERS + ".?!");
            while (tokens.hasMoreTokens()) {
                String w = clean(tokens.nextToken());
                if (!w.isEmpty()) {
                    words.add(w);
                }
            }
        }
        return words;
    }

    /** Character frequencies of the lines (one space between lines). */
    public static CharFrequency frequencies(List<String> lines) {
        CharFrequency f = new CharFrequency();
        for (String line : lines) {
            f.addText(line);
            f.addText(" ");
        }
        return f;
    }

    /**
     * Imports a user's own vocabulary file (any plain text) into the model
     * (Unit III: FileReader).
     *
     * @return number of words added
     */
    public static int importVocabulary(LanguageModel model, File file) throws CorpusFormatException {
        List<String> lines;
        try {
            lines = readLines(new FileReader(file), file.getName());
        } catch (IOException e) {
            throw new CorpusFormatException("Cannot open " + file.getName() + ": " + e.getMessage(), e);
        }
        long before = model.getTokens();
        train(model, lines);
        return (int) (model.getTokens() - before);
    }
}
