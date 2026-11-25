package com.moiclathy.screencoach.service;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.moiclathy.screencoach.data.AppSettings;

/** Non-touchable sample of the compact overlay shown at its real saved position. */
public final class CompactPositionPreview {
    private final Context context;
    private final WindowManager windowManager;
    private final FrameLayout root;
    private final CompactPixelView view;
    private final WindowManager.LayoutParams params;
    private final int touchPad;
    private boolean showing;
    private boolean secure;
    private boolean overStatusBar;

    public CompactPositionPreview(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) throw new IllegalStateException("无法访问窗口服务");
        if (!Settings.canDrawOverlays(context)) throw new IllegalStateException("没有悬浮窗权限");
        touchPad = CompactGeometry.touchPad(context);
        secure = AppSettings.isFlagSecureEnabled(context);
        overStatusBar = AppSettings.isCompactOverStatusBar(context);

        root = new FrameLayout(context);
        root.setPadding(touchPad, touchPad, touchPad, touchPad);
        root.setBackgroundColor(Color.TRANSPARENT);
        view = new CompactPixelView(context);
        view.setText("1B", false);
        view.setStatusColor(0xFF22C55E);
        root.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        if (secure) flags |= WindowManager.LayoutParams.FLAG_SECURE;
        if (overStatusBar) flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        params = new WindowManager.LayoutParams(
                CompactGeometry.MIN_CONTENT_WIDTH + touchPad * 2,
                CompactGeometry.CONTENT_HEIGHT + touchPad * 2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        applyInsetsPolicy();
        readPosition();
    }

    public boolean isShowing() {
        return showing;
    }

    public void show() {
        if (showing) {
            updatePosition();
            return;
        }
        try {
            readPosition();
            windowManager.addView(root, params);
            showing = true;
        } catch (Throwable t) {
            showing = false;
            throw new IllegalStateException("位置预览失败：" + t.getClass().getSimpleName(), t);
        }
    }

    public void hide() {
        if (!showing) return;
        showing = false;
        try { windowManager.removeView(root); } catch (Throwable ignored) { }
    }

    public void updatePosition() {
        readPosition();
        if (!showing) return;
        try { windowManager.updateViewLayout(root, params); } catch (Throwable ignored) { }
    }

    public void setAllowOverStatusBar(boolean allowed) {
        overStatusBar = allowed;
        if (allowed) {
            params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        } else {
            params.flags &= ~WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        }
        applyInsetsPolicy();
        readPosition();
        if (showing) {
            try { windowManager.updateViewLayout(root, params); } catch (Throwable ignored) { }
        }
    }

    public void setSecure(boolean enabled) {
        secure = enabled;
        if (enabled) {
            params.flags |= WindowManager.LayoutParams.FLAG_SECURE;
        } else {
            params.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
        }
        if (showing) {
            try { windowManager.updateViewLayout(root, params); } catch (Throwable ignored) { }
        }
    }

    private void applyInsetsPolicy() {
        // Keep both preview and live overlay in the same full-screen coordinate
        // system; the setting controls NO_LIMITS and the minimum y clamp.
        params.setFitInsetsTypes(0);
        params.layoutInDisplayCutoutMode = overStatusBar
                ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
    }

    private void readPosition() {
        int defaultX = CompactGeometry.defaultX(context);
        int defaultY = CompactGeometry.defaultY(context);
        params.x = CompactGeometry.clampX(context,
                AppSettings.compactX(context, defaultX), params.width);
        params.y = CompactGeometry.clampY(context,
                AppSettings.compactY(context, defaultY), params.height);
    }
}
