package de.noisebleed.dingsbums;

import static de.noisebleed.dingsbums.LetterState.ABSENT;
import static de.noisebleed.dingsbums.LetterState.CORRECT;
import static de.noisebleed.dingsbums.LetterState.PRESENT;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;

public class GameTest {

    @Test
    public void evaluateMarksCorrectPresentAbsent() {
        assertArrayEquals(new LetterState[]{CORRECT, PRESENT, ABSENT, ABSENT, PRESENT},
                Game.evaluate("HEBEL", "HALLE"));
    }

    @Test
    public void evaluateCountsDuplicatesOnlyAsOftenAsInSolution() {
        // Nur zwei L in HALLO, beide an der richtigen Stelle — die übrigen bleiben grau.
        assertArrayEquals(new LetterState[]{ABSENT, ABSENT, CORRECT, CORRECT, ABSENT},
                Game.evaluate("LLLLL", "HALLO"));
        // Ein E in der Lösung: nur das erste nicht-grüne E wird gelb.
        assertArrayEquals(new LetterState[]{PRESENT, ABSENT, ABSENT, ABSENT, ABSENT},
                Game.evaluate("EEABC", "DFGHE"));
    }

    @Test
    public void evaluateGreenTakesPrecedenceOverEarlierYellow() {
        // Das zweite A ist grün und verbraucht das einzige A — das erste bleibt grau.
        assertArrayEquals(new LetterState[]{ABSENT, CORRECT, ABSENT, ABSENT, ABSENT},
                Game.evaluate("AAXYZ", "BAUMS"));
    }

    @Test
    public void umlautsAreOwnLetters() {
        assertArrayEquals(new LetterState[]{ABSENT, CORRECT, CORRECT, CORRECT, CORRECT},
                Game.evaluate("HÖREN", "LÖREN"));
        assertEquals(ABSENT, Game.evaluate("SOHLE", "SÖHNE")[1]);
    }

    @Test
    public void winAfterCorrectGuess() {
        Game game = new Game("TISCH");
        typeWord(game, "STUHL");
        assertEquals(Game.SubmitResult.ACCEPTED, game.submit(w -> true));
        typeWord(game, "TISCH");
        assertEquals(Game.SubmitResult.ACCEPTED, game.submit(w -> false));
        assertEquals(Game.Status.WON, game.getStatus());
        assertFalse(game.type('A'));
    }

    @Test
    public void lossAfterSixWrongGuesses() {
        Game game = new Game("TISCH");
        for (int i = 0; i < Game.MAX_GUESSES; i++) {
            assertEquals(Game.Status.PLAYING, game.getStatus());
            typeWord(game, "STUHL");
            game.submit(w -> true);
        }
        assertEquals(Game.Status.LOST, game.getStatus());
    }

    @Test
    public void rejectsShortAndUnknownWords() {
        Game game = new Game("TISCH");
        typeWord(game, "STU");
        assertEquals(Game.SubmitResult.TOO_SHORT, game.submit(w -> true));
        typeWord(game, "HL");
        assertEquals(Game.SubmitResult.UNKNOWN_WORD, game.submit(w -> false));
        assertEquals("STUHL", game.getCurrent());
        assertEquals(0, game.getGuesses().size());
    }

    @Test
    public void keyStatesKeepBestState() {
        Game game = new Game("TISCH");
        typeWord(game, "SCHAL");
        game.submit(w -> true);
        typeWord(game, "TASSE");
        game.submit(w -> true);
        Map<Character, LetterState> states = game.keyStates(2);
        assertEquals(CORRECT, states.get('T'));
        assertEquals(CORRECT, states.get('S'));
        assertEquals(PRESENT, states.get('C'));
        assertEquals(ABSENT, states.get('A'));
        assertNull(states.get('Z'));
        // Nur die erste Zeile: S war da erst gelb.
        assertEquals(PRESENT, game.keyStates(1).get('S'));
    }

    @Test
    public void serializeRoundTrip() {
        Game game = new Game("SÖHNE");
        typeWord(game, "SOHLE");
        game.submit(w -> true);
        typeWord(game, "BÄ");
        Game copy = Game.deserialize(game.serialize());
        assertEquals("SÖHNE", copy.getSolution());
        assertEquals(game.getGuesses(), copy.getGuesses());
        assertEquals("BÄ", copy.getCurrent());
    }

    @Test
    public void cursorOverwritesAndAdvances() {
        Game game = new Game("TISCH");
        typeWord(game, "STUHL");
        assertEquals(Game.WORD_LENGTH, game.getCursor());
        assertFalse(game.type('X'));
        game.setCursor(1);
        typeWord(game, "IS");
        assertEquals("SISHL", game.getCurrent());
        assertEquals(3, game.getCursor());
    }

    @Test
    public void deleteClearsUnderCursorOrLeftOfIt() {
        Game game = new Game("TISCH");
        typeWord(game, "STUHL");
        game.setCursor(2);
        // Buchstabe unter dem Cursor: löschen, Cursor bleibt.
        game.delete();
        assertEquals("ST HL", game.getCurrent());
        assertEquals(2, game.getCursor());
        // Feld leer: links davon löschen und dorthin rücken.
        game.delete();
        assertEquals("S  HL", game.getCurrent());
        assertEquals(1, game.getCursor());
    }

    @Test
    public void gapsPreventSubmit() {
        Game game = new Game("TISCH");
        typeWord(game, "STUHL");
        game.setCursor(2);
        game.delete();
        assertEquals(Game.SubmitResult.TOO_SHORT, game.submit(w -> true));
        typeWord(game, "U");
        assertEquals(Game.SubmitResult.ACCEPTED, game.submit(w -> true));
        assertEquals("", game.getCurrent());
        assertEquals(0, game.getCursor());
    }

    @Test
    public void serializeKeepsGapsAndCursor() {
        Game game = new Game("TISCH");
        typeWord(game, "STU");
        game.setCursor(1);
        game.delete();
        Game copy = Game.deserialize(game.serialize());
        assertEquals("S U", copy.getCurrent());
        assertEquals(1, copy.getCursor());
    }

    @Test
    public void deserializeVersion10PutsCursorBehindInput() {
        Game game = Game.deserialize("TISCH;STUHL;BÄ");
        assertEquals("BÄ", game.getCurrent());
        assertEquals(2, game.getCursor());
    }

    @Test
    public void fillReplacesInputAndPutsCursorBehind() {
        Game game = new Game("TISCH");
        typeWord(game, "AB");
        game.setCursor(0);
        assertTrue(game.fill("autos"));
        assertEquals("AUTOS", game.getCurrent());
        assertEquals(Game.WORD_LENGTH, game.getCursor());
        assertFalse(game.fill("ZU"));
        assertEquals(Game.SubmitResult.ACCEPTED, game.submit(w -> true));
    }

    private static void typeWord(Game game, String word) {
        for (char c : word.toCharArray()) {
            game.type(c);
        }
    }
}
