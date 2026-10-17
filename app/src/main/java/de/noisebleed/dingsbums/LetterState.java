package de.noisebleed.dingsbums;

/**
 * Bewertung eines Buchstabens. Die Reihenfolge ist eine Rangfolge: auf der Tastatur gewinnt immer
 * die beste bisher bekannte Aussage über einen Buchstaben.
 */
public enum LetterState {
    /** Noch nicht geraten. */
    UNKNOWN,
    /** Kommt im Wort nicht (oder nicht so oft) vor — grau. */
    ABSENT,
    /** Kommt im Wort vor, aber an anderer Stelle — gelb. */
    PRESENT,
    /** Richtiger Buchstabe an richtiger Stelle — grün. */
    CORRECT;

    public LetterState best(LetterState other) {
        return other.ordinal() > ordinal() ? other : this;
    }
}
