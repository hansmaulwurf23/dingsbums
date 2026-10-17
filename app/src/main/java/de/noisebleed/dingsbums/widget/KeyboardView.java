package de.noisebleed.dingsbums.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import de.noisebleed.dingsbums.LetterState;
import de.noisebleed.dingsbums.R;

/**
 * Bildschirmtastatur im QWERTZ-Layout mit Umlauten. Jede Taste trägt die Farbe des besten bisher
 * bekannten Zustands ihres Buchstabens: grau, gelb oder grün.
 */
public class KeyboardView extends View {

    public interface Listener {
        void onLetter(char letter);

        void onEnter();

        void onDelete();
    }

    private static final char ENTER = '\n';
    private static final char DELETE = '\b';
    /** Breite der Sondertasten in Buchstabentasten. */
    private static final float WIDE = 2f;
    private static final String[] ROWS = {"QWERTZUIOPÜ", "ASDFGHJKLÖÄ", "\nYXCVBNM\b"};
    private static final int COLUMNS = 11;

    private static final class Key {
        final char code;
        final RectF rect = new RectF();

        Key(char code) {
            this.code = code;
        }
    }

    private final List<Key> keys = new ArrayList<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path backspace = new Path();

    private final int colorKey;
    private final int colorKeyPressed;
    private final int colorAbsent;
    private final int colorPresent;
    private final int colorCorrect;
    private final float gap;
    private final float keyHeight;
    private final float corner;

    private Map<Character, LetterState> states = Collections.emptyMap();
    private Listener listener;
    private Key pressed;

    public KeyboardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        colorKey = ContextCompat.getColor(context, R.color.key);
        colorKeyPressed = ContextCompat.getColor(context, R.color.key_pressed);
        colorAbsent = ContextCompat.getColor(context, R.color.key_absent);
        colorPresent = ContextCompat.getColor(context, R.color.state_present);
        colorCorrect = ContextCompat.getColor(context, R.color.state_correct);
        float density = getResources().getDisplayMetrics().density;
        gap = 5 * density;
        keyHeight = 56 * density;
        corner = 5 * density;
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        text.setColor(ContextCompat.getColor(context, R.color.text_primary));
        icon.setStyle(Paint.Style.STROKE);
        icon.setStrokeWidth(2 * density);
        icon.setStrokeJoin(Paint.Join.ROUND);
        icon.setStrokeCap(Paint.Cap.ROUND);
        icon.setColor(text.getColor());
        for (String row : ROWS) {
            for (char c : row.toCharArray()) {
                keys.add(new Key(c));
            }
        }
        setHapticFeedbackEnabled(true);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setStates(@NonNull Map<Character, LetterState> states) {
        this.states = states;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = Math.round(ROWS.length * keyHeight + (ROWS.length + 1) * gap)
                + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float avail = w - getPaddingLeft() - getPaddingRight();
        float keyWidth = (avail - gap * (COLUMNS + 1)) / COLUMNS;
        float rowHeight = (h - getPaddingTop() - getPaddingBottom() - gap * (ROWS.length + 1)) / ROWS.length;
        int index = 0;
        for (int r = 0; r < ROWS.length; r++) {
            String row = ROWS[r];
            float rowWidth = -gap;
            for (char c : row.toCharArray()) {
                rowWidth += widthOf(c, keyWidth) + gap;
            }
            float x = getPaddingLeft() + (avail - rowWidth) / 2;
            float y = getPaddingTop() + gap + r * (rowHeight + gap);
            for (char c : row.toCharArray()) {
                float kw = widthOf(c, keyWidth);
                keys.get(index++).rect.set(x, y, x + kw, y + rowHeight);
                x += kw + gap;
            }
        }
        text.setTextSize(Math.min(keyWidth * 0.62f, rowHeight * 0.42f));
        buildBackspaceIcon(rowHeight * 0.36f);
    }

    private float widthOf(char c, float keyWidth) {
        return c == ENTER || c == DELETE ? keyWidth * WIDE + gap * (WIDE - 1) : keyWidth;
    }

    /** Pfeil mit Kreuz, um (0,0) zentriert. */
    private void buildBackspaceIcon(float h) {
        float w = h * 1.4f;
        backspace.reset();
        backspace.moveTo(-w / 2, 0);
        backspace.lineTo(-w / 2 + h / 2, -h / 2);
        backspace.lineTo(w / 2, -h / 2);
        backspace.lineTo(w / 2, h / 2);
        backspace.lineTo(-w / 2 + h / 2, h / 2);
        backspace.close();
        float cx = w / 8, s = h / 5;
        backspace.moveTo(cx - s, -s);
        backspace.lineTo(cx + s, s);
        backspace.moveTo(cx + s, -s);
        backspace.lineTo(cx - s, s);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float textOffset = -(text.descent() + text.ascent()) / 2;
        for (Key key : keys) {
            fill.setColor(key == pressed ? colorKeyPressed : colorFor(key.code));
            canvas.drawRoundRect(key.rect, corner, corner, fill);
            float cx = key.rect.centerX(), cy = key.rect.centerY();
            if (key.code == DELETE) {
                canvas.save();
                canvas.translate(cx, cy);
                canvas.drawPath(backspace, icon);
                canvas.restore();
            } else if (key.code == ENTER) {
                float size = text.getTextSize();
                text.setTextSize(size * 0.62f);
                canvas.drawText(getContext().getString(R.string.key_enter), cx,
                        cy - (text.descent() + text.ascent()) / 2, text);
                text.setTextSize(size);
            } else {
                canvas.drawText(String.valueOf(key.code), cx, cy + textOffset, text);
            }
        }
    }

    private int colorFor(char code) {
        LetterState state = states.get(code);
        if (state == null) {
            return colorKey;
        }
        switch (state) {
            case CORRECT:
                return colorCorrect;
            case PRESENT:
                return colorPresent;
            case ABSENT:
                return colorAbsent;
            default:
                return colorKey;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                Key key = keyAt(event.getX(), event.getY());
                if (key != pressed) {
                    pressed = key;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                Key released = keyAt(event.getX(), event.getY());
                pressed = null;
                invalidate();
                if (released != null) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    dispatch(released.code);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = null;
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void dispatch(char code) {
        if (listener == null) {
            return;
        }
        if (code == ENTER) {
            listener.onEnter();
        } else if (code == DELETE) {
            listener.onDelete();
        } else {
            listener.onLetter(code);
        }
    }

    /** Die Taste unter dem Finger; Lücken zählen zur nächstgelegenen Taste derselben Zeile. */
    @Nullable
    private Key keyAt(float x, float y) {
        Key best = null;
        float bestDist = Float.MAX_VALUE;
        for (Key key : keys) {
            RectF r = key.rect;
            if (y < r.top - gap / 2 || y > r.bottom + gap / 2) {
                continue;
            }
            float dx = x < r.left ? r.left - x : x > r.right ? x - r.right : 0;
            if (dx < bestDist && dx <= gap) {
                bestDist = dx;
                best = key;
            }
        }
        return best;
    }
}
