package com.moiclathy.screencoach.service;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import com.moiclathy.screencoach.R;
import com.moiclathy.screencoach.data.AppSettings;
import com.moiclathy.screencoach.util.UiUtils;

/**
 * Large overlay: a colored answer block with a deliberately oversized "next"
 * touch target. Its secure flag follows the user's global FLAG_SECURE setting.
 */
public final class OverlayController implements AnswerPresenter {
    public interface Callback {
        void onNext();
        void onRetry();
    }

    private static final int COLOR_GREEN = 0xFF16A34A;
    private static final int COLOR_YELLOW = 0xFFFBBF24;
    private static final int COLOR_RED = 0xFFDC2626;
    private static final int COLOR_IDLE = 0xFF475569;
    private static final int TEXT_LIGHT = 0xFFF8FAFC;
    private static final int TEXT_DARK = 0xFF111827;

    private final Context context;
    private final WindowManager windowManager;
    private final Callback callback;
    private final LinearLayout root;
    private final LinearLayout content;
    private final TextView questionView;
    private final TextView stateView;
    private final TextView headlineView;
    private final TextView detailView;
    private final TextView statusView;
    private final Button nextButton;
    private final WindowManager.LayoutParams params;
    private boolean attached;
    private boolean secure;
    private boolean errorState;
    private int lastQuestionNo = -1;
    private String lastAnswer = "";
    private String lastDetail = "";

    public OverlayController(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) throw new IllegalStateException("无法访问窗口服务");
        if (!Settings.canDrawOverlays(context)) {
            throw new IllegalStateException("没有悬浮窗权限");
        }

        int pad = dp(14);
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.setElevation(dp(20));
        root.setBackground(UiUtils.roundedStroke(context, COLOR_IDLE, 22,
                0x55FFFFFF, 1));
        root.setClipToOutline(true);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, dp(10), pad, dp(8));
        content.setClickable(true);
        content.setFocusable(true);
        content.setContentDescription(context.getString(R.string.overlay_drag));
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        questionView = new TextView(context);
        questionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        questionView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        questionView.setLetterSpacing(0.04f);
        header.addView(questionView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Space spacer = new Space(context);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        stateView = new TextView(context);
        stateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        stateView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        stateView.setLetterSpacing(0.03f);
        stateView.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(stateView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView handle = new TextView(context);
        handle.setText("⋮⋮");
        handle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        handle.setAlpha(0.55f);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        handleParams.leftMargin = dp(8);
        header.addView(handle, handleParams);

        headlineView = new TextView(context);
        headlineView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 54);
        headlineView.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        headlineView.setIncludeFontPadding(false);
        headlineView.setMaxLines(3);
        headlineView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        headlineView.setAutoSizeTextTypeUniformWithConfiguration(24, 72, 2,
                TypedValue.COMPLEX_UNIT_SP);
        headlineView.setLineSpacing(0f, 0.92f);
        LinearLayout.LayoutParams headlineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headlineParams.topMargin = dp(6);
        content.addView(headlineView, headlineParams);

        detailView = new TextView(context);
        detailView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        detailView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        detailView.setMaxLines(6);
        detailView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detailView.setLineSpacing(dp(2), 1f);
        detailView.setAutoSizeTextTypeUniformWithConfiguration(11, 18, 1,
                TypedValue.COMPLEX_UNIT_SP);
        detailView.setVisibility(View.GONE);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(8);
        content.addView(detailView, detailParams);

        statusView = new TextView(context);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusView.setMaxLines(4);
        statusView.setLineSpacing(dp(1), 1f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(8);
        content.addView(statusView, statusParams);

        nextButton = new Button(context);
        boolean translationMode = AppSettings.load(context).recognitionMode
                == AppSettings.MODE_TRANSLATION;
        int nextLabel = translationMode ? R.string.overlay_retranslate : R.string.overlay_next;
        nextButton.setText(context.getString(nextLabel));
        nextButton.setAllCaps(false);
        nextButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
        nextButton.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        nextButton.setTextColor(0xFF04121A);
        GradientDrawable buttonShape = UiUtils.rounded(context, 0xFF22D3EE, 16);
        nextButton.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33000000), buttonShape, null));
        nextButton.setGravity(Gravity.CENTER);
        nextButton.setMinHeight(0);
        nextButton.setMinimumHeight(0);
        nextButton.setPadding(dp(12), dp(18), dp(12), dp(18));
        nextButton.setStateListAnimator(null);
        nextButton.setElevation(dp(4));
        nextButton.setContentDescription(context.getString(nextLabel));
        nextButton.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(110).start())
                    .start();
            if (callback != null) callback.onNext();
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(82));
        buttonParams.topMargin = dp(4);
        root.addView(nextButton, buttonParams);

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int width = Math.min(metrics.widthPixels - dp(24), dp(520));
        int savedX = AppSettings.overlayX(context, metrics.widthPixels - width - dp(12));
        int savedY = AppSettings.overlayY(context, dp(104));
        secure = AppSettings.isFlagSecureEnabled(context);
        int windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (secure) windowFlags |= WindowManager.LayoutParams.FLAG_SECURE;
        params = new WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                windowFlags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Math.max(0, Math.min(savedX, Math.max(0, metrics.widthPixels - width)));
        params.y = Math.max(0, Math.min(savedY, Math.max(0, metrics.heightPixels - dp(160))));
        params.windowAnimations = 0;

        installDrag(content);
        content.setOnClickListener(v -> {
            if (errorState && callback != null) callback.onRetry();
        });
    }

    public void show() {
        if (attached) return;
        try {
            root.setAlpha(0f);
            windowManager.addView(root, params);
            attached = true;
            root.animate().alpha(1f).setDuration(160).start();
            showSearching(1, context.getString(R.string.overlay_wait));
        } catch (Throwable t) {
            attached = false;
            throw new IllegalStateException("悬浮窗添加失败：" + t.getClass().getSimpleName(), t);
        }
    }

    public void showSearching(int questionNo, String status) {
        errorState = false;
        applyBackground(COLOR_YELLOW, TEXT_DARK);
        boolean translation = questionNo <= 0 && lastQuestionNo <= 0;
        String label = translation ? "翻译"
                : (lastQuestionNo > 0
                        ? context.getString(R.string.overlay_question, lastQuestionNo)
                        : context.getString(R.string.overlay_question, Math.max(1, questionNo)));
        questionView.setText(label);
        stateView.setText("黄色 · 搜索中");
        if (!lastAnswer.isEmpty()) {
            headlineView.setText(choiceHeadline(lastQuestionNo, lastAnswer));
            detailView.setVisibility(View.GONE);
        } else {
            headlineView.setText("…");
            detailView.setVisibility(View.GONE);
        }
        statusView.setText(status);
        NotificationHelper.updateSession(context, "黄色 · " + status);
    }

    public void showAnswer(int questionNo, String answer, String detail, boolean cached) {
        errorState = false;
        lastQuestionNo = questionNo;
        lastAnswer = answer == null ? "" : answer;
        lastDetail = detail == null ? "" : detail;
        boolean translation = questionNo <= 0;
        applyBackground(COLOR_GREEN, TEXT_LIGHT);
        questionView.setText(translation ? "翻译"
                : context.getString(R.string.overlay_question, questionNo));
        stateView.setText("绿色 · 已落库");
        headlineView.setText(choiceHeadline(questionNo, lastAnswer));
        setDetail(lastDetail);
        statusView.setText(cached ? context.getString(R.string.overlay_cached)
                : context.getString(R.string.overlay_saved));
        NotificationHelper.updateSession(context, translation
                ? "绿色 · 翻译已落库"
                : "绿色 · 第" + questionNo + "题 " + lastAnswer + " 已落库");
    }

    public void showError(int failedQuestionNo, String message) {
        errorState = true;
        applyBackground(COLOR_RED, TEXT_LIGHT);
        boolean translation = failedQuestionNo <= 0;
        if ((translation || lastQuestionNo > 0) && !lastAnswer.isEmpty()) {
            questionView.setText(translation || lastQuestionNo <= 0 ? "翻译"
                    : context.getString(R.string.overlay_question, lastQuestionNo));
            headlineView.setText(choiceHeadline(lastQuestionNo, lastAnswer));
            setDetail(lastDetail);
        } else {
            questionView.setText(translation ? "翻译"
                    : context.getString(R.string.overlay_question, Math.max(1, failedQuestionNo)));
            headlineView.setText("?");
            detailView.setVisibility(View.GONE);
        }
        String clean = message == null || message.trim().isEmpty() ? "未知错误" : message.trim();
        String status = context.getString(R.string.overlay_error, clean);
        if (!translation && lastQuestionNo > 0 && lastQuestionNo != failedQuestionNo) {
            status = "第" + failedQuestionNo + "题 · " + status;
        }
        statusView.setText(status + "\n点按色块立即重试");
        content.setClickable(true);
        NotificationHelper.updateSession(context, translation
                ? "红色 · 翻译失败，稍后重试"
                : "红色 · 第" + failedQuestionNo + "题失败，稍后重试");
    }

    public void updateSearchingStatus(String status) {
        statusView.setText(status);
    }

    private void setDetail(String detail) {
        if (detail == null || detail.trim().isEmpty()) {
            detailView.setVisibility(View.GONE);
            return;
        }
        String value = detail.trim();
        if (value.length() > 900) value = value.substring(0, 900) + "…";
        detailView.setText(value);
        detailView.setVisibility(View.VISIBLE);
    }

    private String choiceHeadline(int questionNo, String answer) {
        String value = answer == null ? "" : answer.trim();
        if (value.isEmpty()) {
            return questionNo > 0 ? String.valueOf(questionNo) + "?" : "?";
        }
        boolean simpleChoice = value.matches("^[A-H](\\s*[,，/]\\s*[A-H])*$");
        if (questionNo <= 0) return value;
        if (simpleChoice) {
            return String.valueOf(questionNo) + value.replaceAll("\\s+", "");
        }
        return value;
    }

    private void applyBackground(int color, int textColor) {
        root.setBackground(UiUtils.roundedStroke(context, color, 22,
                color == COLOR_YELLOW ? 0x66FFFFFF : 0x44FFFFFF, 1));
        questionView.setTextColor(textColor);
        stateView.setTextColor(textColor);
        headlineView.setTextColor(textColor);
        detailView.setTextColor(textColor);
        statusView.setTextColor(textColor);
        if (textColor == TEXT_DARK) {
            statusView.setAlpha(0.82f);
            detailView.setAlpha(0.88f);
            stateView.setAlpha(0.8f);
        } else {
            statusView.setAlpha(0.92f);
            detailView.setAlpha(0.95f);
            stateView.setAlpha(0.9f);
        }
    }

    private void installDrag(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private float startX;
            private float startY;
            private int startWindowX;
            private int startWindowY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        startWindowX = params.x;
                        startWindowY = params.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX;
                        float dy = event.getRawY() - startY;
                        if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) moved = true;
                        if (moved) {
                            params.x = Math.max(0, startWindowX + Math.round(dx));
                            params.y = Math.max(0, startWindowY + Math.round(dy));
                            safeUpdate();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            AppSettings.saveOverlayPosition(context, params.x, params.y);
                        } else {
                            v.performClick();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void safeUpdate() {
        if (!attached) return;
        try {
            windowManager.updateViewLayout(root, params);
        } catch (Throwable ignored) { }
    }

    @Override
    public void setSecure(boolean enabled) {
        secure = enabled;
        if (enabled) {
            params.flags |= WindowManager.LayoutParams.FLAG_SECURE;
        } else {
            params.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
        }
        safeUpdate();
    }

    public void remove() {
        if (!attached) return;
        attached = false;
        try { windowManager.removeView(root); } catch (Throwable ignored) { }
    }

    private int dp(float value) {
        return UiUtils.dp(context, value);
    }
}
