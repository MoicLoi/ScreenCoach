package com.moiclathy.screencoach.util;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

public final class UiUtils {
    private UiUtils() { }

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, context.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable rounded(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable roundedStroke(Context context, int color, float radiusDp,
                                                 int strokeColor, float strokeDp) {
        GradientDrawable drawable = rounded(context, color, radiusDp);
        drawable.setStroke(dp(context, strokeDp), strokeColor);
        return drawable;
    }

    public static GradientDrawable circle(Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }
}
