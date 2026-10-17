package de.noisebleed.dingsbums;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.Locale;

import de.noisebleed.dingsbums.widget.BoardView;
import de.noisebleed.dingsbums.widget.DistributionView;
import de.noisebleed.dingsbums.widget.KeyboardView;

public class MainActivity extends AppCompatActivity implements KeyboardView.Listener {

    private static final int[] WON_TITLES = {
            R.string.won_title_1, R.string.won_title_2, R.string.won_title_3,
            R.string.won_title_4, R.string.won_title_5, R.string.won_title_6,
    };

    private BoardView board;
    private KeyboardView keyboard;
    private WordList words;
    private Game game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        board = findViewById(R.id.board);
        keyboard = findViewById(R.id.keyboard);
        keyboard.setListener(this);
        board.setOnInputTileTapListener(this::moveCursor);

        words = WordList.get(this);
        game = GameStore.loadOrNew(this);
        showGame();
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
        new MaterialAlertDialogBuilder(this)
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

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this).setView(content);
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

    private void showAbout() {
        View content = getLayoutInflater().inflate(R.layout.dialog_about, null, false);
        TextView version = content.findViewById(R.id.about_version);
        version.setText(getString(R.string.about_version,
                BuildConfig.VERSION_NAME, BuildConfig.GIT_SHA));
        new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
