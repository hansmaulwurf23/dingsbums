package de.noisebleed.dingsbums.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
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
    private final float gap;
    private final float corner;

    private Game game;

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
        float density = getResources().getDisplayMetrics().density;
        gap = 6 * density;
        corner = 3 * density;
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2 * density);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        text.setColor(colorText);
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

    @Override
    protected void onDraw(Canvas canvas) {
        if (game == null) {
            return;
        }
        float availW = getWidth() - getPaddingLeft() - getPaddingRight();
        float availH = getHeight() - getPaddingTop() - getPaddingBottom();
        float size = Math.min((availW - gap * (Game.WORD_LENGTH - 1)) / Game.WORD_LENGTH,
                (availH - gap * (Game.MAX_GUESSES - 1)) / Game.MAX_GUESSES);
        size = Math.min(size, 72 * getResources().getDisplayMetrics().density);
        float boardW = size * Game.WORD_LENGTH + gap * (Game.WORD_LENGTH - 1);
        float boardH = size * Game.MAX_GUESSES + gap * (Game.MAX_GUESSES - 1);
        float left0 = getPaddingLeft() + (availW - boardW) / 2;
        float top0 = getPaddingTop() + (availH - boardH) / 2;
        text.setTextSize(size * 0.55f);
        float textOffset = -(text.descent() + text.ascent()) / 2;

        int submitted = game.getGuesses().size();
        String current = game.getCurrent();
        for (int row = 0; row < Game.MAX_GUESSES; row++) {
            String letters;
            LetterState[] eval = null;
            if (row < submitted) {
                letters = game.getGuesses().get(row);
                eval = game.evaluate(row);
            } else if (row == submitted && game.getStatus() == Game.Status.PLAYING) {
                letters = current;
            } else {
                letters = "";
            }
            float rowShift = row == submitted ? shakeOffset : 0;
            for (int col = 0; col < Game.WORD_LENGTH; col++) {
                float left = left0 + col * (size + gap) + rowShift;
                float top = top0 + row * (size + gap);
                tile.set(left, top, left + size, top + size);
                char letter = col < letters.length() ? letters.charAt(col) : 0;

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

    private void drawTile(Canvas canvas, char letter, LetterState state, float scaleY, float textOffset) {
        canvas.save();
        canvas.scale(1f, scaleY, tile.centerX(), tile.centerY());
        if (state == LetterState.UNKNOWN) {
            border.setColor(letter != 0 ? colorFilledBorder : colorEmptyBorder);
            float inset = border.getStrokeWidth() / 2;
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
