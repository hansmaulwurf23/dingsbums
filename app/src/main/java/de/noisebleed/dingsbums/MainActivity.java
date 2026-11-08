package de.noisebleed.dingsbums;

import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.noisebleed.dingsbums.update.AppUpdater;
import de.noisebleed.dingsbums.widget.BoardView;
import de.noisebleed.dingsbums.widget.DistributionView;
import de.noisebleed.dingsbums.widget.KeyboardView;

public class MainActivity extends AppCompatActivity
        implements KeyboardView.Listener, BoardView.TapListener {

    /** Eröffnungswörter per Doppeltipp auf die erste bzw. zweite Zeile. */
    private static final String OPENER_1 = "AUTOS";
    private static final String OPENER_2 = "REICH";

    private static final int[] WON_TITLES = {
            R.string.won_title_1, R.string.won_title_2, R.string.won_title_3,
            R.string.won_title_4, R.string.won_title_5, R.string.won_title_6,
    };

    private static final String TAG = "MainActivity";

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"), this::exportTo);
    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::importFrom);

    private BoardView board;
    private KeyboardView keyboard;
    private WordList words;
    private Game game;

    /** Pro Start nur einmal von selbst auf ein Update hinweisen, nicht bei jedem onResume. */
    private boolean updateOffered;
    /** Nach der Freigabe "Apps aus dieser Quelle" die Installation erneut anstoßen. */
    private boolean installAfterPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        board = findViewById(R.id.board);
        keyboard = findViewById(R.id.keyboard);
        keyboard.setListener(this);
        board.setTapListener(this);

        words = WordList.get(this);
        game = GameStore.loadOrNew(this);
        showGame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (installAfterPermission) {
            installAfterPermission = !AppUpdater.install(this);
            return;
        }
        if (AppUpdater.readyVersionName(this) != null) {
            offerUpdate();
        } else {
            AppUpdater.checkIfDue(this, (version, error) -> {
                if (version != null) {
                    offerUpdate();
                }
            });
        }
    }

    private void showGame() {
        board.setGame(game);
        keyboard.setStates(game.keyStates(game.getGuesses().size()));
    }

    // ---------------------------------------------------------------- Eingabe

    @Override
    public void onLetter(char letter) {
        if (!board.isRevealing() && game.type(letter)) {
            GameStore.save(this, game);
            board.invalidate();
        }
    }

    @Override
    public void onInputTileTapped(int column) {
        moveCursor(column);
    }

    /**
     * Doppeltipp auf die leere erste Zeile trägt AUTOS ein, auf die leere zweite REICH; ist dabei
     * auch die erste noch leer, kommen beide nacheinander. Eingetragen heißt abgeschickt — anders
     * wird die zweite Zeile nie zur Eingabezeile. Schon getippte Buchstaben bleiben unangetastet.
     */
    @Override
    public void onRowDoubleTapped(int row) {
        if (board.isRevealing() || game.getStatus() != Game.Status.PLAYING
                || !game.getCurrent().isEmpty()) {
            return;
        }
        int done = game.getGuesses().size();
        if (row == 0 && done == 0) {
            submitWord(OPENER_1, null);
        } else if (row == 1 && done == 0) {
            submitWord(OPENER_1, () -> submitWord(OPENER_2, null));
        } else if (row == 1 && done == 1) {
            submitWord(OPENER_2, null);
        }
    }

    private void submitWord(String word, @Nullable Runnable afterReveal) {
        if (game.fill(word)) {
            submit(afterReveal);
        }
    }

    private void moveCursor(int column) {
        if (!board.isRevealing() && game.setCursor(column)) {
            GameStore.save(this, game);
            board.invalidate();
        }
    }

    @Override
    public void onDelete() {
        if (!board.isRevealing() && game.delete()) {
            GameStore.save(this, game);
            board.invalidate();
        }
    }

    @Override
    public void onEnter() {
        submit(null);
    }

    /** Schickt die Eingabezeile ab; {@code afterReveal} läuft nach dem Umklappen, falls weiter gespielt wird. */
    private void submit(@Nullable Runnable afterReveal) {
        if (board.isRevealing()) {
            return;
        }
        switch (game.submit(words::isAllowed)) {
            case TOO_SHORT:
                rejectGuess(R.string.hint_too_short);
                break;
            case UNKNOWN_WORD:
                rejectGuess(R.string.hint_unknown_word);
                break;
            case ACCEPTED:
                // Sofort speichern und verbuchen: wer während der Animation die App schließt,
                // soll weder den Versuch noch das Ergebnis verlieren.
                GameStore.save(this, game);
                GameStore.recordResult(this, game);
                int row = game.getGuesses().size() - 1;
                board.reveal(row, () -> {
                    keyboard.setStates(game.keyStates(row + 1));
                    if (game.getStatus() != Game.Status.PLAYING) {
                        showStats(true);
                    } else if (afterReveal != null) {
                        afterReveal.run();
                    }
                });
                break;
            case NOT_PLAYING:
                showStats(true);
                break;
        }
    }

    private void rejectGuess(@StringRes int message) {
        board.shake();
        Snackbar.make(board, message, Snackbar.LENGTH_SHORT).setAnchorView(keyboard).show();
    }

    /** Hardware- oder Bluetooth-Tastatur, praktisch im Emulator. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            onEnter();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            onDelete();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            moveCursor(game.getCursor() - 1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            moveCursor(game.getCursor() + 1);
            return true;
        }
        char c = Character.toUpperCase((char) event.getUnicodeChar());
        if ((c >= 'A' && c <= 'Z') || c == 'Ä' || c == 'Ö' || c == 'Ü') {
            onLetter(c);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------------------------------------------------------------- Menü

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.new_game) {
            requestNewGame();
            return true;
        }
        if (id == R.id.statistics) {
            showStats(false);
            return true;
        }
        try {
            if (id == R.id.export_stats) {
                requestExport();
                return true;
            }
            if (id == R.id.import_stats) {
                requestImport();
                return true;
            }
        } catch (ActivityNotFoundException e) {
            // Kein Dateiauswahl-Dialog, z. B. in einem eingeschränkten Kinderprofil.
            message(getString(R.string.no_file_picker));
            return true;
        }
        if (id == R.id.check_update) {
            checkForUpdate();
            return true;
        }
        if (id == R.id.about) {
            showAbout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Ein neues Wort mitten in der Runde gilt als aufgegeben — sonst ließe sich die Statistik schönen,
     * indem man aussichtslose Runden einfach abbricht. Ohne einen einzigen Versuch zählt es nicht.
     */
    private void requestNewGame() {
        if (game.getStatus() != Game.Status.PLAYING || game.getGuesses().isEmpty()) {
            startNewGame();
            return;
        }
        Dialogs.builder(this)
                .setTitle(R.string.give_up_title)
                .setMessage(R.string.give_up_text)
                .setPositiveButton(R.string.give_up_confirm, (d, w) -> {
                    Stats.recordLoss(this);
                    Snackbar.make(board, getString(R.string.solution_was, game.getSolution()),
                            Snackbar.LENGTH_LONG).setAnchorView(keyboard).show();
                    startNewGame();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startNewGame() {
        game = GameStore.newGame(this);
        showGame();
    }

    private void showStats(boolean afterGame) {
        Stats stats = Stats.load(this);
        View content = getLayoutInflater().inflate(R.layout.dialog_stats, null, false);

        if (afterGame && game.getStatus() != Game.Status.PLAYING) {
            TextView title = content.findViewById(R.id.result_title);
            TextView text = content.findViewById(R.id.result_text);
            int tries = game.getGuesses().size();
            if (game.getStatus() == Game.Status.WON) {
                title.setText(WON_TITLES[tries - 1]);
                text.setText(getResources().getQuantityString(R.plurals.won_text, tries, tries));
            } else {
                title.setText(R.string.lost_title);
                text.setText(getString(R.string.solution_was, game.getSolution()));
            }
            title.setVisibility(View.VISIBLE);
            text.setVisibility(View.VISIBLE);
        }

        setStat(content, R.id.stat_played, stats.played);
        setStat(content, R.id.stat_win_percent, stats.winPercent());
        setStat(content, R.id.stat_streak, stats.streak);
        setStat(content, R.id.stat_max_streak, stats.maxStreak);

        boolean justWon = game.getStatus() == Game.Status.WON;
        DistributionView distribution = content.findViewById(R.id.distribution);
        distribution.setValues(stats.distribution, justWon ? stats.lastGuesses - 1 : -1);

        TextView average = content.findViewById(R.id.stat_average);
        int solved = 0, sum = 0;
        for (int i = 0; i < stats.distribution.length; i++) {
            solved += stats.distribution[i];
            sum += stats.distribution[i] * (i + 1);
        }
        if (solved > 0) {
            average.setText(getString(R.string.stat_average,
                    String.format(Locale.GERMANY, "%.2f", (float) sum / solved)));
        } else {
            average.setVisibility(View.GONE);
        }

        MaterialAlertDialogBuilder builder = Dialogs.builder(this).setView(content);
        if (game.getStatus() != Game.Status.PLAYING) {
            builder.setPositiveButton(R.string.new_game, (d, w) -> startNewGame())
                    .setNegativeButton(R.string.close, null);
        } else {
            builder.setPositiveButton(R.string.close, null);
        }
        builder.show();
    }

    private static void setStat(View content, int id, int value) {
        TextView view = content.findViewById(id);
        view.setText(String.valueOf(value));
    }

    // ---------------------------------------------------------------- Statistik exportieren/importieren

    private void requestExport() {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        exportLauncher.launch("dingsbums-statistik-" + date + ".json");
    }

    private void requestImport() {
        // Manche Dateimanager kennen .json nicht als application/json.
        importLauncher.launch(new String[]{"application/json", "text/plain", "application/octet-stream"});
    }

    private void exportTo(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) {
                throw new IOException("Datei nicht beschreibbar");
            }
            StatsBackup backup = StatsBackup.of(Stats.load(this), GameStore.recentWords(this));
            out.write(backup.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
            message(getString(R.string.export_done));
        } catch (IOException | JSONException | SecurityException e) {
            Log.e(TAG, "Export fehlgeschlagen", e);
            message(getString(R.string.export_failed, e.getMessage()));
        }
    }

    private void importFrom(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        StatsBackup backup;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Datei nicht lesbar");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            for (int n; (n = in.read(chunk)) > 0; ) {
                buffer.write(chunk, 0, n);
            }
            backup = StatsBackup.fromJson(buffer.toString("UTF-8"));
        } catch (IOException | JSONException | SecurityException e) {
            Log.e(TAG, "Import fehlgeschlagen", e);
            message(getString(R.string.import_failed, e.getMessage()));
            return;
        }
        int current = Stats.load(this).played;
        if (current == 0) {
            applyImport(backup);
            return;
        }
        Dialogs.builder(this)
                .setTitle(R.string.import_replace_title)
                .setMessage(getResources().getQuantityString(R.plurals.import_replace_text, current,
                        current, backup.played))
                .setPositiveButton(R.string.import_replace_confirm, (d, w) -> applyImport(backup))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyImport(StatsBackup backup) {
        Stats.replace(this, backup);
        GameStore.addRecent(this, backup.recent);
        message(getResources().getQuantityString(R.plurals.import_done, backup.played, backup.played));
        showStats(false);
    }

    private void message(String text) {
        Snackbar.make(board, text, Snackbar.LENGTH_LONG).setAnchorView(keyboard).show();
    }

    // ---------------------------------------------------------------- Update

    private void checkForUpdate() {
        if (!AppUpdater.isAllowed(this)) {
            Snackbar.make(board, R.string.update_not_allowed, Snackbar.LENGTH_LONG)
                    .setAnchorView(keyboard).show();
            return;
        }
        Snackbar.make(board, R.string.update_checking, Snackbar.LENGTH_SHORT).setAnchorView(keyboard).show();
        AppUpdater.check(this, (version, error) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (version != null) {
                updateOffered = false;
                offerUpdate();
            } else {
                String message = error != null
                        ? getString(R.string.update_failed, error)
                        : getString(R.string.update_none, BuildConfig.VERSION_NAME);
                Snackbar.make(board, message, Snackbar.LENGTH_LONG).setAnchorView(keyboard).show();
            }
        });
    }

    private void offerUpdate() {
        String version = AppUpdater.readyVersionName(this);
        if (version == null || updateOffered || isFinishing() || isDestroyed()) {
            return;
        }
        updateOffered = true;
        Dialogs.builder(this)
                .setTitle(R.string.update_available_title)
                .setMessage(getString(R.string.update_available_text, version, BuildConfig.VERSION_NAME))
                .setPositiveButton(R.string.update_install,
                        (d, w) -> installAfterPermission = !AppUpdater.install(this))
                .setNegativeButton(R.string.update_later, null)
                .show();
    }

    private void showAbout() {
        View content = getLayoutInflater().inflate(R.layout.dialog_about, null, false);
        TextView version = content.findViewById(R.id.about_version);
        version.setText(getString(R.string.about_version,
                BuildConfig.VERSION_NAME, BuildConfig.GIT_SHA));
        Dialogs.builder(this)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
