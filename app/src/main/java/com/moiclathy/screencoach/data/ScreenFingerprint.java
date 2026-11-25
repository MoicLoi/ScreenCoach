package com.moiclathy.screencoach.data;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Stable cache key: question text when available, otherwise a 64-bit visual hash. */
public final class ScreenFingerprint {
    public final String key;
    public final String imageHash;

    private ScreenFingerprint(String key, String imageHash) {
        this.key = key;
        this.imageHash = imageHash;
    }

    public static ScreenFingerprint from(Bitmap bitmap, String visibleText, String packageName) {
        String normalized = normalize(visibleText);
        String imageHash = visualHash(bitmap);
        String packagePart = packageName == null ? "" : packageName;
        if (normalized.length() >= 8) {
            return new ScreenFingerprint("t:" + sha256(packagePart + "\n" + normalized), imageHash);
        }
        return new ScreenFingerprint("p:" + imageHash, imageHash);
    }

    public static String normalize(String text) {
        if (text == null) return "";
        String value = text.replace('\u0000', ' ')
                .replaceAll("[\\s\\u00A0]+", " ")
                .trim();
        if (value.length() > 6000) value = value.substring(0, 6000);
        return value;
    }

    /**
     * Difference hash of the central 84% of the screen. The status bar and
     * gesture bar are excluded so clock changes do not invalidate the cache.
     */
    public static String visualHash(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return "0";
        int top = Math.max(0, (int) (bitmap.getHeight() * 0.08f));
        int bottom = Math.max(top + 8, (int) (bitmap.getHeight() * 0.92f));
        Bitmap cropped = Bitmap.createBitmap(bitmap, 0, top, bitmap.getWidth(),
                Math.min(bottom - top, bitmap.getHeight() - top));
        Bitmap scaled = Bitmap.createScaledBitmap(cropped, 9, 8, true);
        long hash = 0L;
        int index = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int left = luminance(scaled.getPixel(x, y));
                int right = luminance(scaled.getPixel(x + 1, y));
                if (left > right) hash |= (1L << index);
                index++;
            }
        }
        if (scaled != cropped) scaled.recycle();
        cropped.recycle();
        return String.format(java.util.Locale.US, "%016x", hash);
    }

    private static int luminance(int color) {
        return (int) (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format(java.util.Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
