package de.noisebleed.dingsbums;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Hält die laufende Runde in den SharedPreferences, damit sie einen Neustart der App übersteht,
 * und merkt sich die letzten Lösungen, damit dasselbe Wort nicht gleich wiederkommt.
 */
public final class GameStore {

    private static final String TAG = "GameStore";
    private static final String PREFS = "game";
    private static final String KEY_GAME = "game";
    private static final String KEY_RECENT = "recent";
    /** Statistik ist für diese Runde schon verbucht — sonst zählte ein Neustart nach Spielende doppelt. */
    private static final String KEY_RECORDED = "recorded";
    private static final int RECENT_MAX = 200;

    private GameStore() {
    }

    public static Game loadOrNew(Context context) {
        String data = prefs(context).getString(KEY_GAME, null);
        if (data != null) {
            try {
                return Game.deserialize(data);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Gespeicherter Spielstand verworfen", e);
            }
        }
        return newGame(context);
    }

    public static Game newGame(Context context) {
        Set<String> recent = recent(context);
        Game game = new Game(WordList.get(context).randomSolution(recent));
        recent.add(game.getSolution());
        while (recent.size() > RECENT_MAX) {
            recent.remove(recent.iterator().next());
        }
        prefs(context).edit()
                .putString(KEY_RECENT, String.join(",", recent))
                .putBoolean(KEY_RECORDED, false)
                .apply();
        save(context, game);
        return game;
    }

    public static void save(Context context, Game game) {
        prefs(context).edit().putString(KEY_GAME, game.serialize()).apply();
    }

    /** Verbucht das Ergebnis einer beendeten Runde genau einmal in der Statistik. */
    public static void recordResult(Context context, Game game) {
        SharedPreferences prefs = prefs(context);
        if (game.getStatus() == Game.Status.PLAYING || prefs.getBoolean(KEY_RECORDED, false)) {
            return;
        }
        if (game.getStatus() == Game.Status.WON) {
            Stats.recordWin(context, game.getGuesses().size());
        } else {
            Stats.recordLoss(context);
        }
        prefs.edit().putBoolean(KEY_RECORDED, true).apply();
    }

    /** Zuletzt gelöste Wörter, älteste zuerst — für den Export der Statistik. */
    public static List<String> recentWords(Context context) {
        return new ArrayList<>(recent(context));
    }

    /** Wörter aus einem Import vormerken, damit sie nicht gleich wieder drankommen. */
    public static void addRecent(Context context, Collection<String> words) {
        // Importierte Wörter sind älter als die auf diesem Gerät gespielten, also nach vorn.
        Set<String> merged = new LinkedHashSet<>(words);
        merged.addAll(recent(context));
        while (merged.size() > RECENT_MAX) {
            merged.remove(merged.iterator().next());
        }
        prefs(context).edit().putString(KEY_RECENT, String.join(",", merged)).apply();
    }

    private static Set<String> recent(Context context) {
        String data = prefs(context).getString(KEY_RECENT, "");
        Set<String> recent = new LinkedHashSet<>();
        if (!data.isEmpty()) {
            recent.addAll(Arrays.asList(data.split(",")));
        }
        return recent;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
