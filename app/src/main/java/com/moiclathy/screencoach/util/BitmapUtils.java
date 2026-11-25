package com.moiclathy.screencoach.util;

import android.graphics.Bitmap;

public final class BitmapUtils {
    private BitmapUtils() { }

    /** Scale down while preserving aspect ratio. Returns the original when it is already small. */
    public static Bitmap maxDimension(Bitmap source, int max) {
        if (source == null || source.isRecycled()) return source;
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        if (longest <= max) return source;
        float scale = max / (float) longest;
        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
        if (scaled != source) source.recycle();
        return scaled;
    }
}
