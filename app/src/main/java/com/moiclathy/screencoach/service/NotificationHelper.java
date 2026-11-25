package com.moiclathy.screencoach.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.provider.Settings;

import com.moiclathy.screencoach.MainActivity;
import com.moiclathy.screencoach.R;

public final class NotificationHelper {
    public static final String CHANNEL_SESSION = "screencoach_session";
    public static final String CHANNEL_WATCHDOG = "screencoach_watchdog";
    public static final int SESSION_NOTIFICATION_ID = 4101;
    public static final int WATCHDOG_NOTIFICATION_ID = 4102;

    private NotificationHelper() { }

    public static void ensureChannels(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel session = new NotificationChannel(CHANNEL_SESSION,
                context.getString(R.string.notif_channel_session),
                NotificationManager.IMPORTANCE_LOW);
        session.setDescription(context.getString(R.string.notif_channel_session_desc));
        session.setShowBadge(false);
        session.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        nm.createNotificationChannel(session);

        NotificationChannel watchdog = new NotificationChannel(CHANNEL_WATCHDOG,
                context.getString(R.string.notif_channel_watchdog),
                NotificationManager.IMPORTANCE_DEFAULT);
        watchdog.setDescription(context.getString(R.string.notif_channel_watchdog_desc));
        watchdog.setShowBadge(true);
        nm.createNotificationChannel(watchdog);
    }

    public static Notification buildSession(Context context, String text) {
        PendingIntent open = PendingIntent.getActivity(context, 10,
                new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent next = PendingIntent.getService(context, 11,
                new Intent(context, CoachService.class).setAction(CoachService.ACTION_NEXT),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(context, 12,
                new Intent(context, CoachService.class).setAction(CoachService.ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_SESSION)
                .setSmallIcon(R.drawable.ic_stat_coach)
                .setContentTitle(context.getString(R.string.notif_session_title))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_stat_coach),
                        context.getString(R.string.notif_action_next), next).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_stat_coach),
                        context.getString(R.string.notif_action_stop), stop).build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }

    public static void updateSession(Context context, String text) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(SESSION_NOTIFICATION_ID, buildSession(context, text));
    }

    public static void showWatchdog(Context context) {
        ensureChannels(context);
        PendingIntent open = PendingIntent.getActivity(context, 20,
                new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_WATCHDOG)
                .setSmallIcon(R.drawable.ic_stat_coach)
                .setContentTitle(context.getString(R.string.notif_watchdog_title))
                .setContentText(context.getString(R.string.notif_watchdog_text))
                .setStyle(new Notification.BigTextStyle().bigText(context.getString(R.string.notif_watchdog_text)))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(WATCHDOG_NOTIFICATION_ID, notification);
    }

    public static void cancelWatchdog(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(WATCHDOG_NOTIFICATION_ID);
    }

    public static boolean notificationsEnabled(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        return nm == null || nm.areNotificationsEnabled();
    }

    public static boolean isNotificationPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            return context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static Intent notificationSettingsIntent(Context context) {
        return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
    }
}
