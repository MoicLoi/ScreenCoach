package com.moiclathy.screencoach.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.moiclathy.screencoach.data.AppSettings;
import com.moiclathy.screencoach.util.BitmapUtils;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * Reads the active window's accessibility tree and provides an optional
 * Android 11+ screenshot fallback. It intentionally does not click or inject
 * gestures into other applications.
 */
public class ScreenCoachAccessibilityService extends AccessibilityService {
    private static final int MAX_NODES = 1200;
    private static final int MAX_TEXT = 8000;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean capturing = false;

    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            AccessibilityBridge.beat();
            mainHandler.postDelayed(this, 30_000L);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityBridge.attach(this);
        AppSettings.setA11yEverEnabled(this, true);
        WatchdogScheduler.schedule(this);
        mainHandler.removeCallbacks(heartbeat);
        mainHandler.post(heartbeat);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityBridge.beat();
    }

    @Override
    public void onInterrupt() { }

    @Override
    public boolean onUnbind(Intent intent) {
        AccessibilityBridge.detach(this);
        mainHandler.removeCallbacks(heartbeat);
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        AccessibilityBridge.attach(this);
        mainHandler.removeCallbacks(heartbeat);
        mainHandler.post(heartbeat);
    }

    @Override
    public void onDestroy() {
        AccessibilityBridge.detach(this);
        mainHandler.removeCallbacks(heartbeat);
        super.onDestroy();
    }

    ScreenSnapshot readScreenInternal() {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null) return ScreenSnapshot.EMPTY;
            String packageName = charSeq(root.getPackageName());
            LinkedHashSet<String> lines = new LinkedHashSet<>();
            StringBuilder text = new StringBuilder();
            ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
            queue.add(root);
            int visited = 0;
            while (!queue.isEmpty() && visited < MAX_NODES) {
                AccessibilityNodeInfo node = queue.removeFirst();
                visited++;
                if (node == null) continue;
                try {
                    if (node.isVisibleToUser() && !node.isPassword()) {
                        String content = charSeq(node.getText());
                        if (!content.isEmpty()) appendLine(lines, text, content);
                        String description = charSeq(node.getContentDescription());
                        if (!description.isEmpty()) appendLine(lines, text, description);
                    }
                    if (text.length() < MAX_TEXT) {
                        int childCount = node.getChildCount();
                        for (int i = 0; i < childCount && i < 80; i++) {
                            AccessibilityNodeInfo child = node.getChild(i);
                            if (child != null) queue.add(child);
                        }
                    }
                } catch (Throwable ignored) {
                    // A window can disappear while being traversed.
                } finally {
                    if (node != root) {
                        try { node.recycle(); } catch (Throwable ignored) { }
                    }
                }
            }
            String joined = text.toString().trim();
            int questionNo = ScreenSnapshot.detectQuestionNo(joined);
            return new ScreenSnapshot(packageName, joined, questionNo);
        } catch (Throwable ignored) {
            return ScreenSnapshot.EMPTY;
        } finally {
            if (root != null) {
                try { root.recycle(); } catch (Throwable ignored) { }
            }
        }
    }

    private static void appendLine(LinkedHashSet<String> seen, StringBuilder text, String value) {
        String line = value.replace('\u0000', ' ').replaceAll("\\s+", " ").trim();
        if (line.isEmpty() || line.length() > 600) return;
        if (seen.add(line)) {
            if (text.length() + line.length() + 1 > MAX_TEXT) return;
            text.append(line).append('\n');
        }
    }

    private static String charSeq(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    void captureInternal(AccessibilityBridge.CaptureCallback callback) {
        if (capturing) {
            callback.onError("上一次无障碍截图尚未完成");
            return;
        }
        capturing = true;
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(),
                    new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(ScreenshotResult screenshot) {
                            try {
                                HardwareBuffer buffer = screenshot.getHardwareBuffer();
                                if (buffer == null) {
                                    callback.onError("无障碍截图没有返回缓冲区");
                                    return;
                                }
                                Bitmap software = null;
                                try {
                                    Bitmap hardware = Bitmap.wrapHardwareBuffer(
                                            buffer, screenshot.getColorSpace());
                                    if (hardware == null) {
                                        callback.onError("无障碍截图解码失败");
                                        return;
                                    }
                                    software = hardware.copy(Bitmap.Config.ARGB_8888, false);
                                    hardware.recycle();
                                } finally {
                                    buffer.close();
                                }
                                if (software == null) {
                                    callback.onError("无障碍截图转换失败");
                                    return;
                                }
                                Bitmap scaled = BitmapUtils.maxDimension(software, 1280);
                                callback.onCaptured(scaled);
                            } catch (Throwable t) {
                                callback.onError("无障碍截图处理失败");
                            } finally {
                                capturing = false;
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            capturing = false;
                            callback.onError(a11yError(errorCode));
                        }
                    });
        } catch (Throwable t) {
            capturing = false;
            callback.onError("无障碍截图调用失败：" + t.getClass().getSimpleName());
        }
    }

    private static String a11yError(int code) {
        switch (code) {
            case 1:
                return "无障碍截图过于频繁，请稍后重试";
            case 2:
                return "无障碍截图内部错误";
            case 3:
                return "没有无障碍截图权限";
            default:
                return String.format(Locale.US, "无障碍截图失败 (code %d)", code);
        }
    }
}
