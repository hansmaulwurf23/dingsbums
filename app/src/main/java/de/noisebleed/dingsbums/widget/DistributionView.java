package de.noisebleed.dingsbums.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import de.noisebleed.dingsbums.R;

/** Balkendiagramm: wie oft mit 1, 2, … 6 Versuchen gelöst wurde. */
public class DistributionView extends View {

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint count = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int colorBar;
    private final int colorHighlight;
    private final float rowHeight;
    private final float rowGap;

    private int[] values = new int[0];
    private int highlight = -1;

    public DistributionView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        rowHeight = 22 * density;
        rowGap = 5 * density;
        colorBar = ContextCompat.getColor(context, R.color.state_absent);
        colorHighlight = ContextCompat.getColor(context, R.color.state_correct);
        label.setColor(ContextCompat.getColor(context, R.color.text_primary));
        label.setTextSize(sp(14));
        label.setTextAlign(Paint.Align.CENTER);
        count.setColor(label.getColor());
        count.setTextSize(sp(13));
        count.setFakeBoldText(true);
        count.setTextAlign(Paint.Align.RIGHT);
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    /** {@code highlight}: Index der hervorgehobenen Zeile (letzte Lösung), -1 für keine. */
    public void setValues(int[] values, int highlight) {
        this.values = values.clone();
        this.highlight = highlight;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = Math.round(values.length * (rowHeight + rowGap));
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int max = 1;
        for (int v : values) {
            max = Math.max(max, v);
        }
        float labelWidth = label.getTextSize() * 1.4f;
        float barLeft = labelWidth;
        float barMax = getWidth() - barLeft;
        float minBar = count.getTextSize() * 1.8f;
        for (int i = 0; i < values.length; i++) {
            float top = i * (rowHeight + rowGap);
            float cy = top + rowHeight / 2;
            canvas.drawText(String.valueOf(i + 1), labelWidth / 2,
                    cy - (label.descent() + label.ascent()) / 2, label);
            float width = Math.max(minBar, barMax * values[i] / max);
            rect.set(barLeft, top, barLeft + width, top + rowHeight);
            bar.setColor(i == highlight ? colorHighlight : colorBar);
            canvas.drawRect(rect, bar);
            canvas.drawText(String.valueOf(values[i]), rect.right - count.getTextSize() * 0.5f,
                    cy - (count.descent() + count.ascent()) / 2, count);
        }
    }
}
