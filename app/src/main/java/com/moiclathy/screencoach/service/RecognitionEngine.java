package com.moiclathy.screencoach.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.moiclathy.screencoach.data.AnswerCache;
import com.moiclathy.screencoach.data.AppSettings;
import com.moiclathy.screencoach.data.CachedAnswer;
import com.moiclathy.screencoach.data.CoachRuntime;
import com.moiclathy.screencoach.data.KnowledgeDb;
import com.moiclathy.screencoach.data.KnowledgeMatcher;
import com.moiclathy.screencoach.data.ScreenFingerprint;
import com.moiclathy.screencoach.data.TranslationLanguage;
import com.moiclathy.screencoach.net.DeepSeekClient;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 3-second capture -> cache -> DeepSeek vision -> SQLite -> presenter loop.
 * Green means the displayed answer is persisted; yellow means a capture or
 * request is in progress; red means an error and the loop will retry in 3s.
 */
public final class RecognitionEngine {
    private static final String TAG = "RecognitionEngine";
    private static final int MAX_JPEG_BYTES = 3_500_000;

    private final Context context;
    private final AnswerPresenter presenter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService workers;
    private final String apiKey;
    private final int thinkingLevel;
    private final long intervalMs;
    private final boolean translationMode;
    private final int translationLanguage;
    private final boolean useQuestionBank;
    private final boolean useGlossary;
    private final KnowledgeMatcher matcher;
    private ProjectionCapture projection;
    private volatile DeepSeekClient attemptClient;
    private int questionNo = 1;
    private int generation = 0;
    private boolean inFlight = false;
    private boolean bypassCache = false;
    private boolean stopped = false;
    private boolean lastCaptureWasAccessibility = false;
    private Runnable captureWatchdog;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (stopped || inFlight) return;
            beginAttempt();
        }
    };

    public RecognitionEngine(Context context, AnswerPresenter presenter, ProjectionCapture projection,
                             AppSettings settings) {
        this.context = context.getApplicationContext();
        this.presenter = presenter;
        this.projection = projection;
        this.workers = Executors.newFixedThreadPool(2, new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger();
            @Override public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "screencoach-io-" + count.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
        this.apiKey = settings.apiKey == null ? "" : settings.apiKey.trim();
        this.thinkingLevel = settings.thinkingLevel;
        this.intervalMs = Math.max(1, settings.apiIntervalSeconds) * 1000L;
        this.translationMode = settings.recognitionMode == AppSettings.MODE_TRANSLATION;
        this.translationLanguage = settings.translationLanguage;
        this.useQuestionBank = settings.useQuestionBank && !translationMode;
        this.useGlossary = settings.useGlossary && translationMode;
        this.matcher = (settings.useQuestionBank || settings.useGlossary)
                ? new KnowledgeMatcher(KnowledgeDb.get(this.context))
                : null;
    }

    public void start() {
        stopped = false;
        if (translationMode) questionNo = 0;
        presenter.show();
        updateSearching(translationMode ? "正在准备翻译当前页面…"
                : "正在准备第" + questionNo + "题…");
        schedule(400L);
    }

    public void stop() {
        if (stopped) return;
        stopped = true;
        generation++;
        inFlight = false;
        mainHandler.removeCallbacks(tick);
        if (captureWatchdog != null) mainHandler.removeCallbacks(captureWatchdog);
        cancelActiveClient();
        workers.shutdownNow();
        presenter.remove();
        CoachRuntime.reset();
    }

    public void nextQuestion() {
        if (stopped) return;
        if (!translationMode) questionNo = Math.max(1, questionNo + 1);
        else questionNo = 0;
        generation++;
        inFlight = false;
        bypassCache = true;
        mainHandler.removeCallbacks(tick);
        cancelActiveClient();
        updateSearching(translationMode ? "正在重新翻译当前页面…"
                : "已点按下一题，正在截屏第" + questionNo + "题…");
        schedule(80L);
    }

    public void retryNow() {
        if (stopped) return;
        generation++;
        inFlight = false;
        mainHandler.removeCallbacks(tick);
        cancelActiveClient();
        updateSearching(translationMode ? "正在重试翻译当前页面…"
                : "正在重试第" + questionNo + "题…");
        schedule(50L);
    }

    /**
     * Compact user-search mode calls this when the whole tiny overlay is tapped.
     * The current screen is captured immediately, bypassing the answer cache,
     * and only that page's answer is shown.
     */
    public void userSearch() {
        if (stopped) return;
        generation++;
        inFlight = false;
        bypassCache = true;
        mainHandler.removeCallbacks(tick);
        if (captureWatchdog != null) mainHandler.removeCallbacks(captureWatchdog);
        cancelActiveClient();
        updateSearching(translationMode ? "正在手动翻译当前页面…"
                : "正在手动搜索当前页面…");
        schedule(50L);
    }

    public void onProjectionStopped() {
        projection = null;
        if (AccessibilityBridge.isConnected() && !stopped) {
            updateSearching("屏幕采集已停止，改用无障碍截图…");
        }
    }

    private void beginAttempt() {
        inFlight = true;
        final int attempt = ++generation;
        attemptClient = new DeepSeekClient(apiKey, thinkingLevel);
        lastCaptureWasAccessibility = false;
        // Keep green/red stable during the silent 3-second recheck. Yellow is
        // shown only when a new screen is actually being requested.
        if (CoachRuntime.state != CoachRuntime.STATE_KNOWN
                && CoachRuntime.state != CoachRuntime.STATE_ERROR) {
            updateSearching("正在截屏第" + questionNo + "题…");
        }
        if (captureWatchdog != null) mainHandler.removeCallbacks(captureWatchdog);
        captureWatchdog = () -> {
            if (!stopped && inFlight && attempt == generation) {
                fail(attempt, "截屏超时，请检查权限后重试");
            }
        };
        mainHandler.postDelayed(captureWatchdog, 12_000L);
        capture(attempt, projection == null);
    }

    private void capture(int attempt, boolean accessibilityFirst) {
        if (accessibilityFirst) {
            lastCaptureWasAccessibility = true;
            AccessibilityBridge.capture(new AccessibilityBridge.CaptureCallback() {
                @Override public void onCaptured(Bitmap bitmap) {
                    handleBitmap(attempt, bitmap);
                }

                @Override public void onError(String message) {
                    if (projection != null) {
                        captureWithProjection(attempt, message);
                    } else {
                        fail(attempt, message);
                    }
                }
            });
            return;
        }
        captureWithProjection(attempt, null);
    }

    private void captureWithProjection(int attempt, String accessibilityError) {
        ProjectionCapture active = projection;
        if (active == null) {
            lastCaptureWasAccessibility = true;
            AccessibilityBridge.capture(new AccessibilityBridge.CaptureCallback() {
                @Override public void onCaptured(Bitmap bitmap) { handleBitmap(attempt, bitmap); }
                @Override public void onError(String message) {
                    fail(attempt, accessibilityError == null ? message
                            : "两种截屏均失败：" + accessibilityError + "；" + message);
                }
            });
            return;
        }
        active.capture(new ProjectionCapture.Callback() {
            @Override public void onCaptured(Bitmap bitmap) {
                lastCaptureWasAccessibility = false;
                handleBitmap(attempt, bitmap);
            }

            @Override public void onError(String message) {
                lastCaptureWasAccessibility = true;
                AccessibilityBridge.capture(new AccessibilityBridge.CaptureCallback() {
                    @Override public void onCaptured(Bitmap bitmap) { handleBitmap(attempt, bitmap); }
                    @Override public void onError(String a11yMessage) {
                        fail(attempt, "屏幕采集失败：" + message + "；无障碍截图：" + a11yMessage);
                    }
                });
            }
        });
    }

    private void handleBitmap(int attempt, Bitmap bitmap) {
        if (captureWatchdog != null) mainHandler.removeCallbacks(captureWatchdog);
        if (attempt != generation || stopped) {
            if (bitmap != null) bitmap.recycle();
            return;
        }
        if (bitmap == null) {
            fail(attempt, "没有获得截图");
            return;
        }
        ScreenSnapshot snapshot = AccessibilityBridge.readScreen();
        if (context.getPackageName().equals(snapshot.packageName)) {
            bitmap.recycle();
            fail(attempt, "当前是屏学助手界面，请切换到要识别或翻译的页面");
            return;
        }
        if (!translationMode && snapshot.questionNo > 0) {
            questionNo = snapshot.questionNo;
        }
        final int detectedQuestion = questionNo;
        final boolean skipCache = bypassCache;
        bypassCache = false;
        updateSearching(translationMode
                ? (lastCaptureWasAccessibility ? "正在翻译当前页面（无障碍）…"
                        : "正在翻译当前页面…")
                : (lastCaptureWasAccessibility
                        ? "正在请求第" + detectedQuestion + "题（无障碍）…"
                        : "正在请求第" + detectedQuestion + "题…"));
        final DeepSeekClient client = attemptClient;
        workers.execute(() -> processCapture(attempt, client, bitmap, snapshot, detectedQuestion, skipCache));
    }

    private void processCapture(int attempt, DeepSeekClient client, Bitmap bitmap, ScreenSnapshot snapshot,
                                int detectedQuestion, boolean skipCache) {
        String fingerprint = null;
        String imageHash = null;
        String visibleText = snapshot.text;
        try {
            ScreenFingerprint print = ScreenFingerprint.from(bitmap, visibleText, snapshot.packageName);
            fingerprint = (translationMode ? "tr:" + translationLanguage + "|" : "qa|") + print.key;
            imageHash = print.imageHash;
            if (!skipCache) {
                CachedAnswer cached = AnswerCache.get(context).find(fingerprint);
                // A "next question" tap raises the expected number. Do not let
                // an older cached row silently replace it with a stale answer.
                if (cached != null && cached.questionNo == detectedQuestion) {
                    final CachedAnswer hit = cached;
                    mainHandler.post(() -> onCacheHit(attempt, hit));
                    return;
                }
            }
            KnowledgeMatcher.Match localMatch = null;
            if (matcher != null) {
                if (translationMode && useGlossary) {
                    localMatch = matcher.matchGlossary(visibleText,
                            TranslationLanguage.isBidirectional(translationLanguage));
                } else if (!translationMode && useQuestionBank) {
                    localMatch = matcher.matchQuestion(visibleText);
                }
            }
            if (localMatch != null) {
                final String finalFingerprint = fingerprint;
                final String finalImageHash = imageHash;
                final KnowledgeMatcher.Match hit = localMatch;
                mainHandler.post(() -> onLocalMatch(attempt, finalFingerprint, finalImageHash,
                        visibleText, detectedQuestion, hit));
                return;
            }
            String jpeg = compressToBase64(bitmap);
            final String finalFingerprint = fingerprint;
            final String finalImageHash = imageHash;
            DeepSeekClient.Answer answer;
            try {
                if (translationMode) {
                    answer = client.translate(jpeg, "image/jpeg", visibleText,
                            snapshot.packageName,
                            TranslationLanguage.prompt(translationLanguage));
                } else {
                    answer = client.solve(jpeg, "image/jpeg", detectedQuestion,
                            visibleText, snapshot.packageName);
                }
            } catch (DeepSeekClient.DeepSeekException e) {
                mainHandler.post(() -> fail(attempt, e.getMessage()));
                return;
            } catch (Throwable t) {
                mainHandler.post(() -> fail(attempt, "请求失败：" + safeMessage(t)));
                return;
            }
            mainHandler.post(() -> onNetworkAnswer(attempt, finalFingerprint, finalImageHash,
                    visibleText, detectedQuestion, answer));
        } catch (Throwable t) {
            mainHandler.post(() -> fail(attempt, "处理截图失败：" + safeMessage(t)));
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private void onCacheHit(int attempt, CachedAnswer cached) {
        if (attempt != generation || stopped) return;
        if (!translationMode) questionNo = cached.questionNo;
        presenter.showAnswer(cached.questionNo, cached.answer, cached.answerText, true);
        inFlight = false;
        CoachRuntime.update(CoachRuntime.STATE_KNOWN, cached.questionNo,
                translationMode ? "绿色 · 命中翻译缓存" : "绿色 · 命中缓存 · 第" + cached.questionNo + "题");
        schedule(intervalMs);
    }

    private void onLocalMatch(int attempt, String fingerprint, String imageHash,
                              String visibleText, int detectedQuestion,
                              KnowledgeMatcher.Match match) {
        if (attempt != generation || stopped) return;
        int qno = translationMode ? 0 : detectedQuestion;
        try {
            AnswerCache.get(context).upsert(fingerprint, imageHash, qno,
                    match.answer, match.detail, 1.0, visibleText);
        } catch (Throwable t) {
            fail(attempt, "题库/词库答案未能写入 SQLite");
            return;
        }
        if (!translationMode) questionNo = qno;
        presenter.showAnswer(qno, match.answer, match.detail, false);
        inFlight = false;
        CoachRuntime.update(CoachRuntime.STATE_KNOWN, qno,
                translationMode ? "绿色 · 词库翻译已落库" : "绿色 · 题库匹配已落库");
        schedule(intervalMs);
    }

    private void onNetworkAnswer(int attempt, String fingerprint, String imageHash,
                                 String visibleText, int detectedQuestion, DeepSeekClient.Answer answer) {
        if (attempt != generation || stopped) return;
        try {
            AnswerCache.get(context).upsert(fingerprint, imageHash, answer.questionNo,
                    answer.answer, answer.answerText, answer.confidence, visibleText);
        } catch (Throwable t) {
            Log.e(TAG, "Unable to persist answer", t);
            fail(attempt, "答案未能写入 SQLite，已拒绝显示绿色");
            return;
        }
        if (!translationMode) questionNo = answer.questionNo;
        int displayNo = translationMode ? 0 : answer.questionNo;
        presenter.showAnswer(displayNo, answer.answer, answer.answerText, false);
        inFlight = false;
        CoachRuntime.update(CoachRuntime.STATE_KNOWN, displayNo,
                translationMode ? "绿色 · 翻译已落库" : "绿色 · 第" + answer.questionNo + "题已落库");
        schedule(intervalMs);
    }

    private void fail(int attempt, String message) {
        if (captureWatchdog != null) mainHandler.removeCallbacks(captureWatchdog);
        if (attempt != generation || stopped) return;
        inFlight = false;
        String safe = message == null || message.trim().isEmpty() ? "未知错误" : message.trim();
        Log.w(TAG, "Attempt failed: " + safe);
        presenter.showError(questionNo, safe);
        CoachRuntime.update(CoachRuntime.STATE_ERROR, questionNo,
                translationMode ? "红色 · 翻译失败" : "红色 · 第" + questionNo + "题失败");
        schedule(intervalMs);
    }

    private void updateSearching(String status) {
        presenter.showSearching(questionNo, status);
        CoachRuntime.update(CoachRuntime.STATE_SEARCHING, questionNo, status);
    }

    private void cancelActiveClient() {
        DeepSeekClient client = attemptClient;
        if (client != null) client.cancel();
    }

    private void schedule(long delayMs) {
        if (stopped) return;
        mainHandler.removeCallbacks(tick);
        mainHandler.postDelayed(tick, Math.max(0L, delayMs));
    }

    private String compressToBase64(Bitmap bitmap) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int quality = 72;
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
        if (output.size() > MAX_JPEG_BYTES) {
            output.reset();
            quality = 50;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
        }
        if (output.size() > MAX_JPEG_BYTES) {
            throw new IllegalStateException("截图压缩后仍然过大");
        }
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "未知错误";
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return t.getClass().getSimpleName();
        }
        return message.length() > 180 ? message.substring(0, 180) + "…" : message;
    }
}
