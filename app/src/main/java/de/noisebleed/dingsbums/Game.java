package de.noisebleed.dingsbums;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Spielstand einer Runde, ohne Android-Abhängigkeiten, damit er sich direkt testen lässt. */
public final class Game {

    public static final int WORD_LENGTH = 5;
    public static final int MAX_GUESSES = 6;

    public enum Status { PLAYING, WON, LOST }

    public enum SubmitResult { TOO_SHORT, UNKNOWN_WORD, ACCEPTED, NOT_PLAYING }

    private final String solution;
    private final List<String> guesses = new ArrayList<>();
    private final StringBuilder current = new StringBuilder();

    public Game(String solution) {
        if (solution.length() != WORD_LENGTH) {
            throw new IllegalArgumentException("Lösung muss " + WORD_LENGTH + " Buchstaben haben: " + solution);
        }
        this.solution = solution;
    }

    public String getSolution() {
        return solution;
    }

    public List<String> getGuesses() {
        return Collections.unmodifiableList(guesses);
    }

    public String getCurrent() {
        return current.toString();
    }

    public Status getStatus() {
        if (!guesses.isEmpty() && guesses.get(guesses.size() - 1).equals(solution)) {
            return Status.WON;
        }
        return guesses.size() >= MAX_GUESSES ? Status.LOST : Status.PLAYING;
    }

    public boolean type(char letter) {
        if (getStatus() != Status.PLAYING || current.length() >= WORD_LENGTH) {
            return false;
        }
        current.append(Character.toUpperCase(letter));
        return true;
    }

    public boolean delete() {
        if (getStatus() != Status.PLAYING || current.length() == 0) {
            return false;
        }
        current.setLength(current.length() - 1);
        return true;
    }

    public SubmitResult submit(Predicate<String> isKnownWord) {
        if (getStatus() != Status.PLAYING) {
            return SubmitResult.NOT_PLAYING;
        }
        if (current.length() < WORD_LENGTH) {
            return SubmitResult.TOO_SHORT;
        }
        String guess = current.toString();
        if (!guess.equals(solution) && !isKnownWord.test(guess)) {
            return SubmitResult.UNKNOWN_WORD;
        }
        guesses.add(guess);
        current.setLength(0);
        return SubmitResult.ACCEPTED;
    }

    /**
     * Bewertet einen Versuch wie das Original: erst die Treffer an richtiger Stelle, dann werden
     * die übrigen Buchstaben nur so oft gelb, wie sie in der Lösung noch unverbraucht vorkommen.
     * Bei Lösung HALLO wird aus LLLLL also nur das dritte und vierte L grün, der Rest grau.
     */
    public static LetterState[] evaluate(String guess, String solution) {
        LetterState[] result = new LetterState[WORD_LENGTH];
        Map<Character, Integer> remaining = new HashMap<>();
        for (int i = 0; i < WORD_LENGTH; i++) {
            char s = solution.charAt(i);
            if (guess.charAt(i) == s) {
                result[i] = LetterState.CORRECT;
            } else {
                remaining.merge(s, 1, Integer::sum);
            }
        }
        for (int i = 0; i < WORD_LENGTH; i++) {
            if (result[i] != null) {
                continue;
            }
            char g = guess.charAt(i);
            Integer left = remaining.get(g);
            if (left != null && left > 0) {
                result[i] = LetterState.PRESENT;
                remaining.put(g, left - 1);
            } else {
                result[i] = LetterState.ABSENT;
            }
        }
        return result;
    }

    public LetterState[] evaluate(int row) {
        return evaluate(guesses.get(row), solution);
    }

    /** Bester bekannter Zustand je Buchstabe über alle (bzw. die ersten {@code rows}) Versuche. */
    public Map<Character, LetterState> keyStates(int rows) {
        Map<Character, LetterState> states = new HashMap<>();
        for (int row = 0; row < Math.min(rows, guesses.size()); row++) {
            String guess = guesses.get(row);
            LetterState[] eval = evaluate(row);
            for (int i = 0; i < WORD_LENGTH; i++) {
                states.merge(guess.charAt(i), eval[i], LetterState::best);
            }
        }
        return states;
    }

    // ---------------------------------------------------------------- Speichern

    /** Kompakte Textform für die SharedPreferences: {@code LÖSUNG;VERSUCH,VERSUCH;EINGABE}. */
    public String serialize() {
        return solution + ";" + String.join(",", guesses) + ";" + current;
    }

    public static Game deserialize(String data) {
        String[] parts = data.split(";", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Spielstand unlesbar: " + data);
        }
        Game game = new Game(parts[0]);
        if (!parts[1].isEmpty()) {
            for (String guess : parts[1].split(",")) {
                if (guess.length() != WORD_LENGTH) {
                    throw new IllegalArgumentException("Versuch unlesbar: " + guess);
                }
                game.guesses.add(guess);
            }
        }
        if (parts[2].length() > WORD_LENGTH) {
            throw new IllegalArgumentException("Eingabe unlesbar: " + parts[2]);
        }
        game.current.append(parts[2]);
        return game;
    }
}
