package de.noisebleed.dingsbums;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

/**
 * Dialoge mit hellem Rand: der dunkle Dialog hebt sich sonst kaum vom ebenso dunklen Spielfeld ab.
 */
public final class Dialogs {

    private Dialogs() {
    }

    public static MaterialAlertDialogBuilder builder(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        MaterialShapeDrawable background = new MaterialShapeDrawable(
                ShapeAppearanceModel.builder().setAllCornerSizes(24 * density).build());
        background.setFillColor(ContextCompat.getColorStateList(context, R.color.dialog_bg));
        background.setStroke(1.5f * density, ContextCompat.getColor(context, R.color.dialog_border));
        return new MaterialAlertDialogBuilder(context).setBackground(background);
    }
}
