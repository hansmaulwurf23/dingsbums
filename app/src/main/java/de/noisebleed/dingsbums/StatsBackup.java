package de.noisebleed.dingsbums;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Statistik als Datei, um sie auf ein anderes Gerät mitzunehmen:
 * {@code {"format": "dingsbums-statistik", "version": 1, "stats": {...}, "recent": [...]}}.
 *
 * <p>Die Statistik besteht nur aus Zählern, einzelne Runden lassen sich nicht wiedererkennen. Ein
 * Import ersetzt sie deshalb, statt sie aufzuaddieren — sonst zählte derselbe Export doppelt, wenn
 * man ihn zweimal einspielt. Die zuletzt gelösten Wörter kommen mit, damit sie auf dem neuen Gerät
 * nicht gleich wieder drankommen.
 */
public final class StatsBackup {

    static final String FORMAT = "dingsbums-statistik";
    static final int VERSION = 1;

    private static final Pattern WORD = Pattern.compile("[A-ZÄÖÜ]{" + Game.WORD_LENGTH + "}");

    public final int played;
    public final int won;
    public final int streak;
    public final int maxStreak;
    public final int lastGuesses;
    /** {@code distribution[i]}: so oft wurde mit i+1 Versuchen gelöst. */
    public final int[] distribution;
    /** Zuletzt gelöste Wörter, älteste zuerst. */
    public final List<String> recent;

    StatsBackup(int played, int won, int streak, int maxStreak, int lastGuesses,
                int[] distribution, Collection<String> recent) {
        this.played = played;
        this.won = won;
        this.streak = streak;
        this.maxStreak = maxStreak;
        this.lastGuesses = lastGuesses;
        this.distribution = distribution.clone();
        this.recent = Collections.unmodifiableList(new ArrayList<>(recent));
    }

    public static StatsBackup of(Stats stats, Collection<String> recent) {
        return new StatsBackup(stats.played, stats.won, stats.streak, stats.maxStreak,
                stats.lastGuesses, stats.distribution, recent);
    }

    public JSONObject toJson() throws JSONException {
        JSONArray dist = new JSONArray();
        for (int count : distribution) {
            dist.put(count);
        }
        JSONObject stats = new JSONObject()
                .put("played", played)
                .put("won", won)
                .put("streak", streak)
                .put("max_streak", maxStreak)
                .put("last_guesses", lastGuesses)
                .put("distribution", dist);
        return new JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("stats", stats)
                .put("recent", new JSONArray(recent));
    }

    /**
     * Liest eine exportierte Statistik. Eine Datei, die keine Dingsbums-Statistik ist oder
     * unmögliche Zahlen enthält, führt zur Ausnahme — dann bleibt die vorhandene Statistik unberührt.
     */
    public static StatsBackup fromJson(String text) throws JSONException {
        JSONObject json = new JSONObject(text);
        if (!FORMAT.equals(json.optString("format"))) {
            throw new JSONException("Keine Dingsbums-Statistik");
        }
        if (json.optInt("version", 0) > VERSION) {
            throw new JSONException("Datei stammt von einer neueren Version");
        }
        JSONObject stats = json.getJSONObject("stats");
        int played = count(stats, "played");
        int won = count(stats, "won");
        int streak = count(stats, "streak");
        int maxStreak = count(stats, "max_streak");
        int lastGuesses = count(stats, "last_guesses");

        JSONArray dist = stats.getJSONArray("distribution");
        if (dist.length() > Game.MAX_GUESSES) {
            throw new JSONException("Zu viele Versuche in der Verteilung");
        }
        int[] distribution = new int[Game.MAX_GUESSES];
        int solved = 0;
        for (int i = 0; i < dist.length(); i++) {
            distribution[i] = dist.getInt(i);
            if (distribution[i] < 0) {
                throw new JSONException("Negative Anzahl in der Verteilung");
            }
            solved += distribution[i];
        }
        if (won > played || solved > won || streak > maxStreak || lastGuesses > Game.MAX_GUESSES) {
            throw new JSONException("Statistik ist widersprüchlich");
        }

        List<String> recent = new ArrayList<>();
        JSONArray words = json.optJSONArray("recent");
        if (words != null) {
            for (int i = 0; i < words.length(); i++) {
                String word = words.optString(i, "").toUpperCase(Locale.GERMAN);
                if (WORD.matcher(word).matches()) {
                    recent.add(word);
                }
            }
        }
        return new StatsBackup(played, won, streak, maxStreak, lastGuesses, distribution, recent);
    }

    private static int count(JSONObject json, String key) throws JSONException {
        int value = json.getInt(key);
        if (value < 0) {
            throw new JSONException("Negativer Wert für " + key);
        }
        return value;
    }
}
