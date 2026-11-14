package de.noisebleed.dingsbums;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Einstellungen in eigenen SharedPreferences, getrennt von Statistik und Spielstand: die
 * Startwörter für den Doppeltipp und der Kindermodus.
 */
public final class Settings {

    public static final String DEFAULT_OPENER_1 = "AUTOS";
    public static final String DEFAULT_OPENER_2 = "REICH";

    private static final String PREFS = "settings";
    private static final String KEY_OPENER_1 = "opener_1";
    private static final String KEY_OPENER_2 = "opener_2";
    private static final String KEY_KIDS_MODE = "kids_mode";

    private Settings() {
    }

    /** Startwort für den Doppeltipp auf die erste Zeile. */
    public static String opener1(Context context) {
        return prefs(context).getString(KEY_OPENER_1, DEFAULT_OPENER_1);
    }

    /** Startwort für den Doppeltipp auf die zweite Zeile. */
    public static String opener2(Context context) {
        return prefs(context).getString(KEY_OPENER_2, DEFAULT_OPENER_2);
    }

    /** Im Kindermodus kommen nur Wörter aus dem Grundschulwortschatz als Lösung dran. */
    public static boolean kidsMode(Context context) {
        return prefs(context).getBoolean(KEY_KIDS_MODE, false);
    }

    public static void save(Context context, String opener1, String opener2, boolean kidsMode) {
        prefs(context).edit()
                .putString(KEY_OPENER_1, normalize(opener1))
                .putString(KEY_OPENER_2, normalize(opener2))
                .putBoolean(KEY_KIDS_MODE, kidsMode)
                .apply();
    }

    /** Großbuchstaben ohne Leerzeichen, so wie die Wortlisten die Wörter führen. */
    public static String normalize(String word) {
        return word.trim().toUpperCase(Locale.GERMAN);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
