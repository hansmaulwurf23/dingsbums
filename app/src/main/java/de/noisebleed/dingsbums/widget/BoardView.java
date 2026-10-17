package de.noisebleed.dingsbums.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import de.noisebleed.dingsbums.Game;
import de.noisebleed.dingsbums.LetterState;
import de.noisebleed.dingsbums.R;

/**
 * Das Raster aus sechs Zeilen zu fünf Feldern. Die zuletzt abgeschickte Zeile wird Feld für Feld
 * umgeklappt; bis dahin zeigt sie noch die neutrale Eingabe, damit die Auflösung spannend bleibt.
 */
public class BoardView extends View {

    /** Antippen eines Feldes der Eingabezeile. */
    public interface OnInputTileTapListener {
        void onInputTileTapped(int column);
    }

    private static final long FLIP_MS = 320L;
    private static final long FLIP_STAGGER_MS = 260L;
    private static final long SHAKE_MS = 420L;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tile = new RectF();

    private final int colorEmptyBorder;
    private final int colorFilledBorder;
    private final int colorAbsent;
    private final int colorPresent;
    private final int colorCorrect;
    private final int colorText;
    private final int colorCurrentFill;
    private final int colorCurrentBorder;
    private final int colorCurrentFilledBorder;
    private final int colorCursor;
    private final float gap;
    private final float corner;
    private final float strokeWidth;

    private Game game;
    private OnInputTileTapListener tapListener;

    /** Raster-Geometrie aus dem letzten {@link #layoutBoard()}, auch für die Trefferprüfung. */
    private float tileSize;
    private float boardLeft;
    private float boardTop;

    /** Zeile, die gerade umgeklappt wird, und die Animationszeit seit Beginn; -1 = keine. */
    private int revealRow = -1;
    private float revealElapsed;
    private ValueAnimator revealAnimator;

    private float shakeOffset;
    private ValueAnimator shakeAnimator;

    public BoardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        colorEmptyBorder = ContextCompat.getColor(context, R.color.tile_border_empty);
        colorFilledBorder = ContextCompat.getColor(context, R.color.tile_border_filled);
        colorAbsent = ContextCompat.getColor(context, R.color.state_absent);
        colorPresent = ContextCompat.getColor(context, R.color.state_present);
        colorCorrect = ContextCompat.getColor(context, R.color.state_correct);
        colorText = ContextCompat.getColor(context, R.color.text_primary);
        colorCurrentFill = ContextCompat.getColor(context, R.color.tile_current_fill);
        colorCurrentBorder = ContextCompat.getColor(context, R.color.tile_current_border);
        colorCurrentFilledBorder = ContextCompat.getColor(context, R.color.tile_current_border_filled);
        colorCursor = ContextCompat.getColor(context, R.color.tile_cursor);
        float density = getResources().getDisplayMetrics().density;
        gap = 6 * density;
        corner = 3 * density;
        strokeWidth = 2 * density;
        border.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        text.setColor(colorText);
    }

    public void setOnInputTileTapListener(OnInputTileTapListener listener) {
        this.tapListener = listener;
    }

    public void setGame(Game game) {
        this.game = game;
        cancelReveal();
        invalidate();
    }

    /** Klappt die Zeile {@code row} auf und ruft danach {@code onDone}. */
    public void reveal(int row, Runnable onDone) {
        cancelReveal();
        revealRow = row;
        long total = FLIP_STAGGER_MS * (Game.WORD_LENGTH - 1) + FLIP_MS;
        revealAnimator = ValueAnimator.ofFloat(0, total);
        revealAnimator.setDuration(total);
        revealAnimator.setInterpolator(new LinearInterpolator());
        revealAnimator.addUpdateListener(a -> {
            revealElapsed = (float) a.getAnimatedValue();
            invalidate();
        });
        revealAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                revealRow = -1;
                invalidate();
                if (!cancelled && onDone != null) {
                    onDone.run();
                }
            }
        });
        revealAnimator.start();
    }

    public boolean isRevealing() {
        return revealRow >= 0;
    }

    private void cancelReveal() {
        if (revealAnimator != null) {
            revealAnimator.cancel();
            revealAnimator = null;
        }
        revealRow = -1;
    }

    /** Schüttelt die Eingabezeile — für zu kurze oder unbekannte Wörter. */
    public void shake() {
        if (shakeAnimator != null) {
            shakeAnimator.cancel();
        }
        float amplitude = 10 * getResources().getDisplayMetrics().density;
        shakeAnimator = ValueAnimator.ofFloat(0, 1);
        shakeAnimator.setDuration(SHAKE_MS);
        shakeAnimator.addUpdateListener(a -> {
            float t = a.getAnimatedFraction();
            shakeOffset = (float) (Math.sin(t * Math.PI * 6) * amplitude * (1 - t));
            invalidate();
        });
        shakeAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelReveal();
        if (shakeAnimator != null) {
            shakeAnimator.cancel();
        }
    }

    private void layoutBoard() {
        float availW = getWidth() - getPaddingLeft() - getPaddingRight();
        float availH = getHeight() - getPaddingTop() - getPaddingBottom();
        float size = Math.min((availW - gap * (Game.WORD_LENGTH - 1)) / Game.WORD_LENGTH,
                (availH - gap * (Game.MAX_GUESSES - 1)) / Game.MAX_GUESSES);
        tileSize = Math.min(size, 72 * getResources().getDisplayMetrics().density);
        float boardW = tileSize * Game.WORD_LENGTH + gap * (Game.WORD_LENGTH - 1);
        float boardH = tileSize * Game.MAX_GUESSES + gap * (Game.MAX_GUESSES - 1);
        boardLeft = getPaddingLeft() + (availW - boardW) / 2;
        boardTop = getPaddingTop() + (availH - boardH) / 2;
    }

    /** Die Eingabezeile, solange noch gespielt wird; sonst -1. */
    private int inputRow() {
        return game != null && game.getStatus() == Game.Status.PLAYING ? game.getGuesses().size() : -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int row = inputRow();
        if (row < 0 || tapListener == null || isRevealing()) {
            return super.onTouchEvent(event);
        }
        float top = boardTop + row * (tileSize + gap);
        // Großzügig: die halbe Lücke ober- und unterhalb zählt noch zur Zeile.
        if (event.getY() < top - gap / 2 || event.getY() > top + tileSize + gap / 2) {
            return super.onTouchEvent(event);
        }
        int column = (int) Math.floor((event.getX() - boardLeft + gap / 2) / (tileSize + gap));
        if (column < 0 || column >= Game.WORD_LENGTH) {
            return super.onTouchEvent(event);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            tapListener.onInputTileTapped(column);
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (game == null) {
            return;
        }
        layoutBoard();
        text.setTextSize(tileSize * 0.55f);
        float textOffset = -(text.descent() + text.ascent()) / 2;

        int submitted = game.getGuesses().size();
        int input = inputRow();
        for (int row = 0; row < Game.MAX_GUESSES; row++) {
            LetterState[] eval = row < submitted ? game.evaluate(row) : null;
            float rowShift = row == input ? shakeOffset : 0;
            for (int col = 0; col < Game.WORD_LENGTH; col++) {
                float left = boardLeft + col * (tileSize + gap) + rowShift;
                float top = boardTop + row * (tileSize + gap);
                tile.set(left, top, left + tileSize, top + tileSize);

                char letter = 0;
                if (row < submitted) {
                    letter = game.getGuesses().get(row).charAt(col);
                } else if (row == input && game.getCurrentLetter(col) != Game.EMPTY) {
                    letter = game.getCurrentLetter(col);
                }

                if (row == input) {
                    drawInputTile(canvas, letter, col == game.getCursor(), textOffset);
                    continue;
                }
                LetterState state = eval != null ? eval[col] : LetterState.UNKNOWN;
                float scaleY = 1f;
                if (row == revealRow) {
                    float t = (revealElapsed - col * FLIP_STAGGER_MS) / FLIP_MS;
                    t = Math.max(0, Math.min(1, t));
                    // Erste Hälfte: neutrales Feld klappt zu, zweite: farbiges klappt auf.
                    if (t < 0.5f) {
                        state = LetterState.UNKNOWN;
                        scaleY = 1 - t * 2;
                    } else {
                        scaleY = (t - 0.5f) * 2;
                    }
                }
                drawTile(canvas, letter, state, scaleY, textOffset);
            }
        }
    }

    /** Feld der Eingabezeile: getönt hinterlegt, das Cursor-Feld mit hellem, dickerem Rahmen. */
    private void drawInputTile(Canvas canvas, char letter, boolean cursor, float textOffset) {
        fill.setColor(colorCurrentFill);
        canvas.drawRoundRect(tile, corner, corner, fill);
        float width = cursor ? strokeWidth * 1.75f : strokeWidth;
        border.setStrokeWidth(width);
        border.setColor(cursor ? colorCursor : letter != 0 ? colorCurrentFilledBorder : colorCurrentBorder);
        float inset = width / 2;
        tile.inset(inset, inset);
        canvas.drawRoundRect(tile, corner, corner, border);
        tile.inset(-inset, -inset);
        if (letter != 0) {
            canvas.drawText(String.valueOf(letter), tile.centerX(), tile.centerY() + textOffset, text);
        }
    }

    private void drawTile(Canvas canvas, char letter, LetterState state, float scaleY, float textOffset) {
        canvas.save();
        canvas.scale(1f, scaleY, tile.centerX(), tile.centerY());
        if (state == LetterState.UNKNOWN) {
            border.setStrokeWidth(strokeWidth);
            border.setColor(letter != 0 ? colorFilledBorder : colorEmptyBorder);
            float inset = strokeWidth / 2;
            tile.inset(inset, inset);
            canvas.drawRoundRect(tile, corner, corner, border);
            tile.inset(-inset, -inset);
        } else {
            fill.setColor(colorFor(state));
            canvas.drawRoundRect(tile, corner, corner, fill);
        }
        if (letter != 0) {
            canvas.drawText(String.valueOf(letter), tile.centerX(), tile.centerY() + textOffset, text);
        }
        canvas.restore();
    }

    private int colorFor(LetterState state) {
        switch (state) {
            case CORRECT:
                return colorCorrect;
            case PRESENT:
                return colorPresent;
            default:
                return colorAbsent;
        }
    }
}
