package com.moiclathy.screencoach.service;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowInsets;
import android.view.WindowManager;

import com.moiclathy.screencoach.data.AppSettings;

/** Shared pixel geometry for the compact overlay and its in-app position preview. */
public final class CompactGeometry {
    public static final int CONTENT_HEIGHT = 24;
    public static final int MIN_CONTENT_WIDTH = 40;
    public static final int COLLAPSED_CONTENT_WIDTH = 80;
    public static final int SQUARE_SIZE = 1;

    private CompactGeometry() { }

    public static int touchPad(Context context) {
        return Math.max(8, Math.round(8f * context.getResources()
                .getDisplayMetrics().density));
    }

    public static int windowWidth(Context context) {
        return MIN_CONTENT_WIDTH + touchPad(context) * 2;
    }

    public static int windowHeight(Context context) {
        return CONTENT_HEIGHT + touchPad(context) * 2;
    }

    public static int screenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    public static int screenHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    public static int defaultX(Context context) {
        return Math.max(0, screenWidth(context) - windowWidth(context)
                - Math.round(12f * context.getResources().getDisplayMetrics().density));
    }

    public static int defaultY(Context context) {
        return Math.max(0, Math.round(104f * context.getResources().getDisplayMetrics().density));
    }

    public static int clampX(Context context, int x) {
        return clampX(context, x, windowWidth(context));
    }

    public static int clampX(Context context, int x, int windowWidth) {
        return Math.max(0, Math.min(x, Math.max(0, screenWidth(context) - windowWidth)));
    }

    /** Minimum y: 0 when covering the status bar is allowed, otherwise below it. */
    public static int minY(Context context) {
        return AppSettings.isCompactOverStatusBar(context) ? 0 : statusBarHeight(context);
    }

    public static int statusBarHeight(Context context) {
        try {
            WindowManager wm = context.getSystemService(WindowManager.class);
            if (wm != null) {
                WindowInsets insets = wm.getCurrentWindowMetrics().getWindowInsets();
                int height = insets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.statusBars()
                                | WindowInsets.Type.displayCutout()).top;
                if (height > 0) return height;
            }
        } catch (Throwable ignored) { }
        int id = context.getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        if (id > 0) {
            int height = context.getResources().getDimensionPixelSize(id);
            if (height > 0) return height;
        }
        return Math.round(24f * context.getResources().getDisplayMetrics().density);
    }

    public static int clampY(Context context, int y) {
        return clampY(context, y, windowHeight(context));
    }

    public static int clampY(Context context, int y, int windowHeight) {
        return Math.max(minY(context),
                Math.min(y, Math.max(minY(context), screenHeight(context) - windowHeight)));
    }
}
