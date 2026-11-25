package com.moiclathy.screencoach.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.moiclathy.screencoach.data.AppSettings;

/** Re-arms only the accessibility health reminder; never auto-starts capture. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (AppSettings.isA11yEverEnabled(context)) {
            WatchdogScheduler.schedule(context);
        }
    }
}
