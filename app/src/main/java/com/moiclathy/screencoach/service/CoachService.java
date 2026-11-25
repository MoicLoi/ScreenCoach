package com.moiclathy.screencoach.service;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.moiclathy.screencoach.data.AppSettings;
import com.moiclathy.screencoach.data.CoachRuntime;

/**
 * Foreground owner of MediaProjection, output presentation and the 3s loop.
 * If the service is stopped, its projection and presenter are released with it.
 */
public class CoachService extends Service {
    private static final String TAG = "CoachService";
    public static final String ACTION_START_MEDIA = "com.moiclathy.screencoach.START_MEDIA";
    public static final String ACTION_START_A11Y = "com.moiclathy.screencoach.START_A11Y";
    public static final String ACTION_STOP = "com.moiclathy.screencoach.STOP";
    public static final String ACTION_NEXT = "com.moiclathy.screencoach.NEXT";
    public static final String ACTION_REFRESH_SECURITY = "com.moiclathy.screencoach.REFRESH_SECURITY";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AnswerPresenter presenter;
    private RecognitionEngine engine;
    private ProjectionCapture projection;
    private boolean foregroundStarted;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.ensureChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            shutdown("用户停止");
            return START_NOT_STICKY;
        }
        if (ACTION_NEXT.equals(action)) {
            if (engine != null) {
                engine.nextQuestion();
            }
            if (engine == null) stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_REFRESH_SECURITY.equals(action)) {
            if (presenter != null) {
                presenter.setSecure(AppSettings.isFlagSecureEnabled(this));
            }
            if (engine == null) stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_START_MEDIA.equals(action) || ACTION_START_A11Y.equals(action)) {
            if (engine != null) {
                NotificationHelper.updateSession(this, "识别已在运行");
                return START_NOT_STICKY;
            }
            startSession(intent, ACTION_START_MEDIA.equals(action));
            return START_NOT_STICKY;
        }
        if (engine == null) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startSession(Intent intent, boolean mediaMode) {
        if (!foregroundStarted) {
            startForeground(NotificationHelper.SESSION_NOTIFICATION_ID,
                    NotificationHelper.buildSession(this, "准备截屏…"));
            foregroundStarted = true;
        }
        AppSettings settings = AppSettings.load(this);
        boolean notificationMode = settings.displayMode == AppSettings.DISPLAY_NOTIFICATION;
        if (!notificationMode && !Settings.canDrawOverlays(this)) {
            shutdownWithMessage("没有悬浮窗权限，无法显示答案");
            return;
        }
        if (notificationMode && !NotificationHelper.isNotificationPermissionGranted(this)) {
            shutdownWithMessage("常驻通知模式需要通知权限");
            return;
        }
        if (mediaMode) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data = getIntentData(intent);
            if (data == null || resultCode == 0) {
                shutdownWithMessage("屏幕采集授权无效");
                return;
            }
            try {
                projection = ProjectionCapture.start(this, resultCode, data, () -> {
                    mainHandler.post(() -> {
                        if (engine != null) engine.onProjectionStopped();
                    });
                });
            } catch (Throwable t) {
                Log.e(TAG, "Projection start failed", t);
                if (AccessibilityBridge.isConnected()) {
                    projection = null;
                } else {
                    shutdownWithMessage("屏幕采集启动失败：" + t.getClass().getSimpleName());
                    return;
                }
            }
        } else {
            if (!AccessibilityBridge.isServiceEnabled(this)) {
                shutdownWithMessage("未启用无障碍服务");
                return;
            }
            WatchdogScheduler.schedule(this);
        }

        CoachRuntime.running = true;
        CoachRuntime.mode = mediaMode ? "media" : "a11y";
        CoachRuntime.questionNo = 1;
        CoachRuntime.state = CoachRuntime.STATE_SEARCHING;
        CoachRuntime.status = "准备截屏…";
        CoachRuntime.intervalSeconds = settings.apiIntervalSeconds;
        try {
            if (notificationMode) {
                presenter = new NotificationPresenter(this);
            } else if (settings.displayMode == AppSettings.DISPLAY_COMPACT) {
                presenter = new CompactOverlayController(this,
                        settings.compactUserSearchMode,
                        () -> {
                            if (engine != null) engine.userSearch();
                        });
            } else {
                presenter = new OverlayController(this, new OverlayController.Callback() {
                    @Override public void onNext() {
                        if (engine != null) engine.nextQuestion();
                    }

                    @Override public void onRetry() {
                        if (engine != null) engine.retryNow();
                    }
                });
            }
            engine = new RecognitionEngine(this, presenter, projection, settings);
            engine.start();
        } catch (Throwable t) {
            Log.e(TAG, "Engine start failed", t);
            shutdownWithMessage("显示层或识别引擎启动失败：" + t.getClass().getSimpleName());
            return;
        }

        WatchdogScheduler.schedule(this);
    }

    @SuppressWarnings("deprecation")
    private Intent getIntentData(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        }
        return intent.getParcelableExtra(EXTRA_RESULT_DATA);
    }

    private void shutdownWithMessage(String message) {
        CoachRuntime.reset();
        NotificationHelper.updateSession(this, "红色 · " + message);
        if (engine != null) engine.stop();
        if (presenter != null) presenter.remove();
        if (projection != null) {
            projection.release();
            projection = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        foregroundStarted = false;
        stopSelf();
    }

    private void shutdown(String reason) {
        if (engine != null) {
            engine.stop();
            engine = null;
        }
        if (projection != null) {
            projection.release();
            projection = null;
        }
        if (presenter != null) {
            presenter.remove();
            presenter = null;
        }
        CoachRuntime.reset();
        stopForeground(STOP_FOREGROUND_REMOVE);
        foregroundStarted = false;
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (engine != null) {
            engine.stop();
            engine = null;
        }
        if (projection != null) {
            projection.release();
            projection = null;
        }
        if (presenter != null) {
            presenter.remove();
            presenter = null;
        }
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foregroundStarted = false;
        }
        CoachRuntime.reset();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
