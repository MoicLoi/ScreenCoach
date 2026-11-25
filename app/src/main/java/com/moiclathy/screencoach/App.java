package com.moiclathy.screencoach;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.WindowManager;

import com.moiclathy.screencoach.data.AnswerCache;
import com.moiclathy.screencoach.data.AppSettings;
import com.moiclathy.screencoach.service.NotificationHelper;

/**
 * App-wide entry point. Every activity gets FLAG_SECURE so the configuration UI
 * (which contains API keys) cannot be captured by screenshots or screen recording.
 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.ensureChannels(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                secure(activity);
            }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) {
                secure(activity);
            }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
        new Thread(() -> {
            try {
                AnswerCache.get(this).prune(24L * 60L * 60L * 1000L);
            } catch (Throwable ignored) {
                // A cache failure must never prevent the UI from opening.
            }
        }, "cache-prune").start();
    }

    private static void secure(Activity activity) {
        boolean enabled = AppSettings.isFlagSecureEnabled(activity);
        if (enabled) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
}
