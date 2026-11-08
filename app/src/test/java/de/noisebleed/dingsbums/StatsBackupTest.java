package de.noisebleed.dingsbums;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.json.JSONException;
import org.junit.Test;

import java.util.Arrays;

public class StatsBackupTest {

    private static final String BOB =
            "{\"format\":\"dingsbums-statistik\",\"version\":1,"
                    + "\"stats\":{\"played\":11,\"won\":6,\"streak\":3,\"max_streak\":3,\"last_guesses\":4,"
                    + "\"distribution\":[0,0,0,1,4,1]},"
                    + "\"recent\":[\"ÜBUNG\",\"raser\",\"zu lang\",\"SPASS\"]}";

    @Test
    public void readsExport() throws JSONException {
        StatsBackup backup = StatsBackup.fromJson(BOB);
        assertEquals(11, backup.played);
        assertEquals(6, backup.won);
        assertEquals(3, backup.streak);
        assertEquals(3, backup.maxStreak);
        assertEquals(4, backup.lastGuesses);
        assertArrayEquals(new int[]{0, 0, 0, 1, 4, 1}, backup.distribution);
        // Kleinschreibung wird angeglichen, Ungültiges fällt raus.
        assertEquals(Arrays.asList("ÜBUNG", "RASER", "SPASS"), backup.recent);
    }

    @Test
    public void roundTrip() throws JSONException {
        StatsBackup original = new StatsBackup(20, 15, 2, 7, 3, new int[]{1, 2, 3, 4, 5, 0},
                Arrays.asList("HALLO", "BETEN"));
        StatsBackup copy = StatsBackup.fromJson(original.toJson().toString());
        assertEquals(original.played, copy.played);
        assertEquals(original.won, copy.won);
        assertEquals(original.streak, copy.streak);
        assertEquals(original.maxStreak, copy.maxStreak);
        assertEquals(original.lastGuesses, copy.lastGuesses);
        assertArrayEquals(original.distribution, copy.distribution);
        assertEquals(original.recent, copy.recent);
    }

    @Test
    public void shortDistributionIsPadded() throws JSONException {
        StatsBackup backup = StatsBackup.fromJson(BOB.replace("[0,0,0,1,4,1]", "[0,1,4,1]"));
        assertArrayEquals(new int[]{0, 1, 4, 1, 0, 0}, backup.distribution);
    }

    @Test
    public void rejectsOtherFiles() {
        assertThrows(JSONException.class, () -> StatsBackup.fromJson("{\"format\":\"zahlen-bestenliste\"}"));
        assertThrows(JSONException.class, () -> StatsBackup.fromJson("kein json"));
        assertThrows(JSONException.class, () -> StatsBackup.fromJson(BOB.replace("\"version\":1", "\"version\":2")));
    }

    @Test
    public void rejectsImpossibleNumbers() {
        // Mehr gewonnen als gespielt.
        assertThrows(JSONException.class, () -> StatsBackup.fromJson(BOB.replace("\"won\":6", "\"won\":12")));
        // Verteilung zählt mehr Lösungen als gewonnen.
        assertThrows(JSONException.class, () -> StatsBackup.fromJson(BOB.replace("[0,0,0,1,4,1]", "[0,0,0,1,4,9]")));
        // Negative Werte.
        assertThrows(JSONException.class, () -> StatsBackup.fromJson(BOB.replace("\"played\":11", "\"played\":-1")));
        // Zu viele Versuchsstufen.
        assertThrows(JSONException.class, () -> StatsBackup.fromJson(BOB.replace("[0,0,0,1,4,1]", "[0,0,0,1,4,1,0]")));
    }
}
