package de.noisebleed.dingsbums;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Die beiden Wortlisten aus den Assets, erzeugt von {@code tools/build_wordlists.py}: gesucht wird
 * nur unter den gebräuchlichen Wörtern, raten darf man jedes zulässige.
 */
public final class WordList {

    private static WordList instance;

    private final List<String> solutions;
    private final Set<String> allowed;
    private final Random random = new SecureRandom();

    private WordList(Context context) {
        solutions = read(context, "solutions.txt");
        allowed = new HashSet<>(read(context, "guesses.txt"));
        allowed.addAll(solutions);
    }

    public static synchronized WordList get(Context context) {
        if (instance == null) {
            instance = new WordList(context.getApplicationContext());
        }
        return instance;
    }

    public boolean isAllowed(String word) {
        return allowed.contains(word);
    }

    /** Ein zufälliges Lösungswort, möglichst keins aus {@code recent}. */
    public String randomSolution(Set<String> recent) {
        for (int attempt = 0; attempt < 20; attempt++) {
            String word = solutions.get(random.nextInt(solutions.size()));
            if (!recent.contains(word)) {
                return word;
            }
        }
        return solutions.get(random.nextInt(solutions.size()));
    }

    private static List<String> read(Context context, String asset) {
        List<String> words = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(asset), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == Game.WORD_LENGTH) {
                    words.add(line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Wortliste " + asset + " nicht lesbar", e);
        }
        return words;
    }
}
