package com.moiclathy.screencoach.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Display;

import com.moiclathy.screencoach.util.BitmapUtils;

/** Static hand-off between the accessibility service and the recognition loop. */
public final class AccessibilityBridge {
    public interface CaptureCallback {
        void onCaptured(Bitmap bitmap);
        void onError(String message);
    }

    private static volatile ScreenCoachAccessibilityService service;
    private static volatile long lastHeartbeat = 0L;

    private AccessibilityBridge() { }

    static void attach(ScreenCoachAccessibilityService instance) {
        service = instance;
        beat();
    }

    static void detach(ScreenCoachAccessibilityService instance) {
        if (service == instance) service = null;
    }

    static void beat() {
        lastHeartbeat = System.currentTimeMillis();
    }

    public static boolean isConnected() {
        return service != null;
    }

    public static long lastHeartbeat() {
        return lastHeartbeat;
    }

    public static ScreenSnapshot readScreen() {
        ScreenCoachAccessibilityService instance = service;
        if (instance == null) return ScreenSnapshot.EMPTY;
        return instance.readScreenInternal();
    }

    public static void capture(CaptureCallback callback) {
        ScreenCoachAccessibilityService instance = service;
        if (instance == null) {
            callback.onError("无障碍服务未连接");
            return;
        }
        instance.captureInternal(callback);
    }

    public static boolean isServiceEnabled(Context context) {
        try {
            String enabled = android.provider.Settings.Secure.getString(
                    context.getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            String packageName = context.getPackageName();
            String fullClass = ScreenCoachAccessibilityService.class.getName();
            String simpleClass = ScreenCoachAccessibilityService.class.getSimpleName();
            String[] entries = enabled.split(":");
            for (String entry : entries) {
                int slash = entry.indexOf('/');
                if (slash <= 0) continue;
                String entryPackage = entry.substring(0, slash);
                String entryClass = entry.substring(slash + 1);
                if (!entryPackage.equals(packageName)) continue;
                if (entryClass.equals(fullClass)
                        || entryClass.equals("." + fullClass)
                        || entryClass.endsWith("." + simpleClass)) {
                    return true;
                }
            }
        } catch (Throwable ignored) { }
        return isConnected();
    }

    public static void captureWithDisplay(CaptureCallback callback) {
        // Kept as a readable alias for callers that don't care about the display id.
        capture(callback);
    }
}
