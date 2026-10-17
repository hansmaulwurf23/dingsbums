package de.noisebleed.dingsbums;

import android.content.Context;
import android.content.SharedPreferences;

/** Statistik über alle Runden: wie oft gespielt, wie oft gelöst und mit wie vielen Versuchen. */
public final class Stats {

    private static final String PREFS = "stats";
    private static final String KEY_PLAYED = "played";
    private static final String KEY_WON = "won";
    private static final String KEY_STREAK = "streak";
    private static final String KEY_MAX_STREAK = "max_streak";
    private static final String KEY_LAST_GUESSES = "last_guesses";
    private static final String KEY_DIST = "dist_";

    public final int played;
    public final int won;
    public final int streak;
    public final int maxStreak;
    /** Versuche der letzten Runde, 0 wenn sie nicht gelöst wurde. */
    public final int lastGuesses;
    /** {@code distribution[i]}: so oft wurde mit i+1 Versuchen gelöst. */
    public final int[] distribution = new int[Game.MAX_GUESSES];

    private Stats(SharedPreferences prefs) {
        played = prefs.getInt(KEY_PLAYED, 0);
        won = prefs.getInt(KEY_WON, 0);
        streak = prefs.getInt(KEY_STREAK, 0);
        maxStreak = prefs.getInt(KEY_MAX_STREAK, 0);
        lastGuesses = prefs.getInt(KEY_LAST_GUESSES, 0);
        for (int i = 0; i < distribution.length; i++) {
            distribution[i] = prefs.getInt(KEY_DIST + (i + 1), 0);
        }
    }

    public static Stats load(Context context) {
        return new Stats(prefs(context));
    }

    public int winPercent() {
        return played == 0 ? 0 : Math.round(100f * won / played);
    }

    public static void recordWin(Context context, int guesses) {
        SharedPreferences prefs = prefs(context);
        int streak = prefs.getInt(KEY_STREAK, 0) + 1;
        String dist = KEY_DIST + guesses;
        prefs.edit()
                .putInt(KEY_PLAYED, prefs.getInt(KEY_PLAYED, 0) + 1)
                .putInt(KEY_WON, prefs.getInt(KEY_WON, 0) + 1)
                .putInt(KEY_STREAK, streak)
                .putInt(KEY_MAX_STREAK, Math.max(streak, prefs.getInt(KEY_MAX_STREAK, 0)))
                .putInt(KEY_LAST_GUESSES, guesses)
                .putInt(dist, prefs.getInt(dist, 0) + 1)
                .apply();
    }

    public static void recordLoss(Context context) {
        SharedPreferences prefs = prefs(context);
        prefs.edit()
                .putInt(KEY_PLAYED, prefs.getInt(KEY_PLAYED, 0) + 1)
                .putInt(KEY_STREAK, 0)
                .putInt(KEY_LAST_GUESSES, 0)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
