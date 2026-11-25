package com.moiclathy.screencoach.service;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.moiclathy.screencoach.R;
import com.moiclathy.screencoach.data.AppSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny pixel overlay. Visible content is only:
 *   <question><answer>  [status square]
 * The plate is white, with dark pixel glyphs. No L, answer_text,
 * progress or other extra content is drawn. The whole window (including a
 * transparent touch margin) is the hotspot.
 */
public final class CompactOverlayController implements AnswerPresenter {
    private static final long EXPAND_MS = 4000L;
    private static final int COLOR_YELLOW = 0xFFFBBF24;
    private static final int COLOR_GREEN = 0xFF22C55E;
    private static final int COLOR_RED = 0xFFEF4444;

    private final Context context;
    private final WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final FrameLayout root;
    private final CompactPixelView view;
    private final WindowManager.LayoutParams params;
    private final int touchPad;
    private final int minVisualWidth = CompactGeometry.MIN_CONTENT_WIDTH;
    private final int collapsedVisualWidth = CompactGeometry.COLLAPSED_CONTENT_WIDTH;
    private final int maxVisualWidth;
    private final List<Item> deck = new ArrayList<>();
    private final boolean userSearchMode;
    private final Runnable searchAction;
    private int index = -1;
    private int stateColor = COLOR_YELLOW;
    private int fallbackQuestionNo = 1;
    private boolean collapsed = false;
    private boolean attached = false;
    private boolean secure;
    private final boolean overStatusBar;
    private Runnable collapseRunnable;

    public CompactOverlayController(Context context, boolean userSearchMode, Runnable searchAction) {
        this.context = context;
        this.userSearchMode = userSearchMode;
        this.searchAction = searchAction;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) throw new IllegalStateException("无法访问窗口服务");
        if (!Settings.canDrawOverlays(context)) {
            throw new IllegalStateException("没有悬浮窗权限");
        }
        secure = AppSettings.isFlagSecureEnabled(context);
        overStatusBar = AppSettings.isCompactOverStatusBar(context);
        touchPad = CompactGeometry.touchPad(context);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        maxVisualWidth = Math.min(640, Math.max(minVisualWidth,
                metrics.widthPixels - touchPad * 2 - Math.round(8f * metrics.density)));

        root = new FrameLayout(context);
        root.setPadding(touchPad, touchPad, touchPad, touchPad);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setContentDescription(context.getString(R.string.compact_overlay_desc));
        root.setClickable(true);
        root.setOnClickListener(v -> onHotspotTapped());

        view = new CompactPixelView(context);
        root.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        if (secure) flags |= WindowManager.LayoutParams.FLAG_SECURE;
        if (overStatusBar) {
            flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        }
        params = new WindowManager.LayoutParams(
                minVisualWidth + touchPad * 2,
                CompactGeometry.CONTENT_HEIGHT + touchPad * 2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        applyInsetsPolicy();
        params.x = AppSettings.compactX(context, CompactGeometry.defaultX(context));
        params.y = AppSettings.compactY(context, CompactGeometry.defaultY(context));
        clampPosition();
    }

    @Override
    public void show() {
        if (attached) return;
        try {
            windowManager.addView(root, params);
            attached = true;
        } catch (Throwable t) {
            attached = false;
            throw new IllegalStateException("小型悬浮窗添加失败：" + t.getClass().getSimpleName(), t);
        }
    }

    @Override
    public void showSearching(int questionNo, String status) {
        fallbackQuestionNo = Math.max(0, questionNo);
        stateColor = COLOR_YELLOW;
        if (index < 0) index = deck.isEmpty() ? -1 : 0;
        updateContent(false, false);
        updateNotification();
    }

    @Override
    public void showAnswer(int questionNo, String answer, String detail, boolean cached) {
        String clean = cleanAnswer(answer);
        Item item = find(questionNo);
        if (item == null) {
            item = new Item(questionNo, clean);
            deck.add(item);
        } else {
            item.answer = clean;
        }
        index = deck.indexOf(item);
        fallbackQuestionNo = questionNo;
        stateColor = COLOR_GREEN;
        if (userSearchMode) {
            // Only the current page answer is shown; no historical cycling.
            deck.clear();
            deck.add(item);
            index = 0;
        }
        updateContent(true, true);
    }

    @Override
    public void showError(int failedQuestionNo, String message) {
        fallbackQuestionNo = Math.max(0, failedQuestionNo);
        stateColor = COLOR_RED;
        updateContent(false, false);
        updateNotification();
    }

    @Override
    public void updateSearchingStatus(String status) {
        // No progress strings are shown in compact mode.
    }

    @Override
    public void setSecure(boolean enabled) {
        secure = enabled;
        if (enabled) {
            params.flags |= WindowManager.LayoutParams.FLAG_SECURE;
        } else {
            params.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
        }
        updateWindow();
    }

    @Override
    public void remove() {
        if (collapseRunnable != null) mainHandler.removeCallbacks(collapseRunnable);
        if (!attached) return;
        attached = false;
        try { windowManager.removeView(root); } catch (Throwable ignored) { }
    }

    private void onHotspotTapped() {
        root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        if (userSearchMode) {
            deck.clear();
            index = -1;
            stateColor = COLOR_YELLOW;
            updateContent(false, false);
            updateNotification();
            if (searchAction != null) searchAction.run();
        } else {
            cycle();
        }
    }

    private void cycle() {
        if (deck.isEmpty()) return;
        index = (index + 1) % deck.size();
        updateContent(true, false);
        updateNotification();
    }

    private void updateContent(boolean resetExpansion, boolean notify) {
        if (resetExpansion) {
            collapsed = false;
            if (collapseRunnable != null) mainHandler.removeCallbacks(collapseRunnable);
        }
        String fullText = displayText();
        int desired = CompactPixelView.desiredWidth(fullText, view.fallbackPaint());
        int visualWidth = Math.max(minVisualWidth, Math.min(desired, maxVisualWidth));
        if (resetExpansion && visualWidth > collapsedVisualWidth) {
            scheduleCollapse();
        } else if (!resetExpansion) {
            visualWidth = Math.max(minVisualWidth,
                    Math.min(params.width - touchPad * 2, maxVisualWidth));
        }
        view.setText(fullText, collapsed);
        view.setStatusColor(stateColor);
        params.width = visualWidth + touchPad * 2;
        params.height = CompactGeometry.CONTENT_HEIGHT + touchPad * 2;
        clampPosition();
        updateWindow();
        if (notify) updateNotification();
    }

    private void scheduleCollapse() {
        if (collapseRunnable != null) mainHandler.removeCallbacks(collapseRunnable);
        collapseRunnable = () -> {
            collapsed = true;
            params.width = collapsedVisualWidth + touchPad * 2;
            updateContent(false, false);
        };
        mainHandler.postDelayed(collapseRunnable, EXPAND_MS);
    }

    private void updateWindow() {
        view.setCollapsed(collapsed);
        if (!attached) return;
        try {
            windowManager.updateViewLayout(root, params);
            root.invalidate();
            view.invalidate();
        } catch (Throwable ignored) { }
    }

    private void applyInsetsPolicy() {
        // Keep both preview and live overlay in the same full-screen coordinate
        // system; the setting controls NO_LIMITS and the minimum y clamp.
        params.setFitInsetsTypes(0);
        params.layoutInDisplayCutoutMode = overStatusBar
                ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
    }

    private void clampPosition() {
        params.x = CompactGeometry.clampX(context, params.x, params.width);
        params.y = CompactGeometry.clampY(context, params.y, params.height);
    }

    private void updateNotification() {
        NotificationHelper.updateSession(context, compactNotificationText());
    }

    private String compactNotificationText() {
        String square;
        if (stateColor == COLOR_GREEN) square = "\uD83D\uDFE9";
        else if (stateColor == COLOR_RED) square = "\uD83D\uDFE5";
        else square = "\uD83D\uDFE8";
        Item item = currentItem();
        String body;
        if (item == null) {
            body = fallbackQuestionNo > 0 ? fallbackQuestionNo + "?" : "?";
        } else {
            body = item.questionNo > 0 ? item.questionNo + item.answer : item.answer;
        }
        return body + " " + square;
    }

    private String displayText() {
        Item item = currentItem();
        if (item == null) {
            return fallbackQuestionNo > 0 ? fallbackQuestionNo + "?" : "?";
        }
        return item.questionNo > 0 ? item.questionNo + item.answer : item.answer;
    }

    private Item currentItem() {
        if (deck.isEmpty()) return null;
        if (index < 0 || index >= deck.size()) index = 0;
        return deck.get(index);
    }

    private Item find(int questionNo) {
        for (Item item : deck) if (item.questionNo == questionNo) return item;
        return null;
    }

    private static String cleanAnswer(String answer) {
        if (answer == null) return "?";
        String value = answer.replaceAll("\\s+", " ").trim();
        if (value.isEmpty()) return "?";
        return value.length() > 160 ? value.substring(0, 160) + ".." : value;
    }

    private static final class Item {
        final int questionNo;
        String answer;
        Item(int questionNo, String answer) {
            this.questionNo = questionNo;
            this.answer = answer;
        }
    }
}
