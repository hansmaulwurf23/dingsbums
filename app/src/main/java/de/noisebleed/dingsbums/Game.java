package de.noisebleed.dingsbums;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    /** Eingabezeile; {@link #EMPTY} markiert ein leeres Feld, auch mitten im Wort. */
    private final char[] current = new char[WORD_LENGTH];
    /** Feld, in das der nächste Buchstabe kommt; {@code WORD_LENGTH} = hinter dem letzten. */
    private int cursor;

    public static final char EMPTY = ' ';

    public Game(String solution) {
        if (solution.length() != WORD_LENGTH) {
            throw new IllegalArgumentException("Lösung muss " + WORD_LENGTH + " Buchstaben haben: " + solution);
        }
        this.solution = solution;
        Arrays.fill(current, EMPTY);
    }

    public String getSolution() {
        return solution;
    }

    public List<String> getGuesses() {
        return Collections.unmodifiableList(guesses);
    }

    /** Die Eingabezeile ohne leere Felder am Ende; Lücken davor stehen als {@link #EMPTY}. */
    public String getCurrent() {
        int end = WORD_LENGTH;
        while (end > 0 && current[end - 1] == EMPTY) {
            end--;
        }
        return new String(current, 0, end);
    }

    /** Buchstabe im Feld {@code index} der Eingabezeile oder {@link #EMPTY}. */
    public char getCurrentLetter(int index) {
        return current[index];
    }

    public int getCursor() {
        return cursor;
    }

    /** Setzt den Cursor auf ein Feld der Eingabezeile, z. B. nach Antippen. */
    public boolean setCursor(int index) {
        if (getStatus() != Status.PLAYING || index < 0 || index >= WORD_LENGTH || index == cursor) {
            return false;
        }
        cursor = index;
        return true;
    }

    public Status getStatus() {
        if (!guesses.isEmpty() && guesses.get(guesses.size() - 1).equals(solution)) {
            return Status.WON;
        }
        return guesses.size() >= MAX_GUESSES ? Status.LOST : Status.PLAYING;
    }

    /** Füllt die ganze Eingabezeile mit {@code word}, der Cursor steht danach dahinter. */
    public boolean fill(String word) {
        if (getStatus() != Status.PLAYING || word.length() != WORD_LENGTH) {
            return false;
        }
        word.toUpperCase(Locale.GERMAN).getChars(0, WORD_LENGTH, current, 0);
        cursor = WORD_LENGTH;
        return true;
    }

    /** Schreibt (oder überschreibt) am Cursor und rückt ein Feld weiter. */
    public boolean type(char letter) {
        if (getStatus() != Status.PLAYING || cursor >= WORD_LENGTH) {
            return false;
        }
        current[cursor++] = Character.toUpperCase(letter);
        return true;
    }

    /**
     * Löscht den Buchstaben unter dem Cursor; ist das Feld leer, den links davon und rückt dorthin.
     * Beim normalen Tippen steht der Cursor hinter dem letzten Buchstaben, dann wirkt das wie gewohnt.
     */
    public boolean delete() {
        if (getStatus() != Status.PLAYING) {
            return false;
        }
        if (cursor < WORD_LENGTH && current[cursor] != EMPTY) {
            current[cursor] = EMPTY;
            return true;
        }
        if (cursor == 0) {
            return false;
        }
        current[--cursor] = EMPTY;
        return true;
    }

    public SubmitResult submit(Predicate<String> isKnownWord) {
        if (getStatus() != Status.PLAYING) {
            return SubmitResult.NOT_PLAYING;
        }
        String guess = new String(current);
        if (guess.indexOf(EMPTY) >= 0) {
            return SubmitResult.TOO_SHORT;
        }
        if (!guess.equals(solution) && !isKnownWord.test(guess)) {
            return SubmitResult.UNKNOWN_WORD;
        }
        guesses.add(guess);
        Arrays.fill(current, EMPTY);
        cursor = 0;
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

    /**
     * Kompakte Textform für die SharedPreferences: {@code LÖSUNG;VERSUCH,VERSUCH;EINGABE;CURSOR}.
     * Leere Felder der Eingabe stehen als Leerzeichen. Stände aus Version 1.0 hatten noch kein
     * Cursor-Feld; dort steht der Cursor hinter der Eingabe.
     */
    public String serialize() {
        return solution + ";" + String.join(",", guesses) + ";" + getCurrent() + ";" + cursor;
    }

    public static Game deserialize(String data) {
        String[] parts = data.split(";", -1);
        if (parts.length != 3 && parts.length != 4) {
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
        String input = parts[2];
        if (input.length() > WORD_LENGTH) {
            throw new IllegalArgumentException("Eingabe unlesbar: " + input);
        }
        input.getChars(0, input.length(), game.current, 0);
        game.cursor = input.length();
        if (parts.length == 4) {
            try {
                game.cursor = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Cursor unlesbar: " + parts[3], e);
            }
            if (game.cursor < 0 || game.cursor > WORD_LENGTH) {
                throw new IllegalArgumentException("Cursor außerhalb: " + parts[3]);
            }
        }
        return game;
    }
}
