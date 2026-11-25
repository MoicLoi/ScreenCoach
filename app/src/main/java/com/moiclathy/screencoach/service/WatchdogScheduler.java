package com.moiclathy.screencoach.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import com.moiclathy.screencoach.data.AppSettings;

/**
 * A visible, system-scheduled accessibility health check. It cannot and does
 * not bypass Android's requirement that the user enable the service.
 */
public final class WatchdogScheduler {
    private static final long INTERVAL_MS = 15L * 60L * 1000L;
    private static final int REQUEST_CODE = 7101;
    public static final String ACTION_CHECK = "com.moiclathy.screencoach.WATCHDOG_CHECK";

    private WatchdogScheduler() { }

    public static void schedule(Context context) {
        if (!AppSettings.isA11yEverEnabled(context)) return;
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager == null) return;
        PendingIntent pendingIntent = pendingIntent(context);
        alarmManager.cancel(pendingIntent);
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + INTERVAL_MS, INTERVAL_MS, pendingIntent);
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager != null) alarmManager.cancel(pendingIntent(context));
    }

    public static void checkNow(Context context) {
        if (!AppSettings.isA11yEverEnabled(context)) {
            NotificationHelper.cancelWatchdog(context);
            return;
        }
        boolean enabled = AccessibilityBridge.isServiceEnabled(context);
        boolean connected = AccessibilityBridge.isConnected();
        long heartbeat = AccessibilityBridge.lastHeartbeat();
        boolean stale = connected && System.currentTimeMillis() - heartbeat > 5L * 60L * 1000L;
        if (!enabled || !connected || stale) {
            NotificationHelper.showWatchdog(context);
        } else {
            NotificationHelper.cancelWatchdog(context);
        }
        schedule(context);
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, WatchdogReceiver.class).setAction(ACTION_CHECK);
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
