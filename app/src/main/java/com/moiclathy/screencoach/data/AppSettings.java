package com.moiclathy.screencoach.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.moiclathy.screencoach.util.SecretStore;

/**
 * User configuration. The only model credential is the DeepSeek API key;
 * it is encrypted with Android Keystore and never stored in plain preferences.
 */
public final class AppSettings {
    private static final String PREFS = "screencoach_settings";
    public static final int DISPLAY_LARGE = 0;
    public static final int DISPLAY_COMPACT = 1;
    public static final int DISPLAY_NOTIFICATION = 2;
    public static final int THINKING_OFF = 0;
    public static final int THINKING_LOW = 1;
    public static final int THINKING_HIGH = 2;
    public static final int MODE_QA = 0;
    public static final int MODE_TRANSLATION = 1;

    private static final String K_CLEAR_ON_START = "clear_cache_on_start";
    private static final String K_DISPLAY_MODE = "display_mode";
    private static final String K_FLAG_SECURE = "flag_secure_enabled";
    private static final String K_API_INTERVAL = "api_interval_seconds";
    private static final String K_COMPACT_USER_SEARCH = "compact_user_search";
    private static final String K_THINKING_LEVEL = "thinking_level";
    private static final String K_RECOGNITION_MODE = "recognition_mode";
    private static final String K_TRANSLATION_LANGUAGE = "translation_language";
    private static final String K_USE_QUESTION_BANK = "use_question_bank";
    private static final String K_USE_GLOSSARY = "use_glossary";
    private static final String K_A11Y_EVER = "accessibility_ever_enabled";
    private static final String K_OVERLAY_X = "overlay_x";
    private static final String K_OVERLAY_Y = "overlay_y";
    private static final String K_COMPACT_X = "compact_x";
    private static final String K_COMPACT_Y = "compact_y";
    private static final String K_COMPACT_OVER_STATUS = "compact_over_status_bar";
    private static final String SECRET_API_KEY = "api_key";

    public String apiKey = "";
    public boolean clearCacheOnStart = false;
    public int displayMode = DISPLAY_LARGE;
    public boolean flagSecureEnabled = true;
    public int apiIntervalSeconds = 3;
    public boolean compactUserSearchMode = false;
    public boolean compactOverStatusBar = false;
    public int thinkingLevel = THINKING_OFF;
    public int recognitionMode = MODE_QA;
    public int translationLanguage = 0;
    public boolean useQuestionBank = true;
    public boolean useGlossary = true;

    public static AppSettings load(Context context) {
        SharedPreferences p = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SecretStore secrets = new SecretStore(context);
        AppSettings settings = new AppSettings();
        settings.apiKey = secrets.get(SECRET_API_KEY);
        settings.clearCacheOnStart = p.getBoolean(K_CLEAR_ON_START, false);
        settings.displayMode = clampDisplayMode(p.getInt(K_DISPLAY_MODE, DISPLAY_LARGE));
        settings.flagSecureEnabled = p.getBoolean(K_FLAG_SECURE, true);
        settings.apiIntervalSeconds = clampInterval(p.getInt(K_API_INTERVAL, 3));
        settings.compactUserSearchMode = p.getBoolean(K_COMPACT_USER_SEARCH, false);
        settings.compactOverStatusBar = p.getBoolean(K_COMPACT_OVER_STATUS, false);
        settings.thinkingLevel = clampThinking(p.getInt(K_THINKING_LEVEL, THINKING_OFF));
        settings.recognitionMode = p.getInt(K_RECOGNITION_MODE, MODE_QA) == MODE_TRANSLATION
                ? MODE_TRANSLATION : MODE_QA;
        settings.translationLanguage = TranslationLanguage.clamp(
                p.getInt(K_TRANSLATION_LANGUAGE, 0));
        settings.useQuestionBank = p.getBoolean(K_USE_QUESTION_BANK, true);
        settings.useGlossary = p.getBoolean(K_USE_GLOSSARY, true);
        return settings;
    }

    public boolean save(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit()
                .putBoolean(K_CLEAR_ON_START, clearCacheOnStart)
                .putInt(K_DISPLAY_MODE, clampDisplayMode(displayMode))
                .putBoolean(K_FLAG_SECURE, flagSecureEnabled)
                .putInt(K_API_INTERVAL, clampInterval(apiIntervalSeconds))
                .putBoolean(K_COMPACT_USER_SEARCH, compactUserSearchMode)
                .putBoolean(K_COMPACT_OVER_STATUS, compactOverStatusBar)
                .putInt(K_THINKING_LEVEL, clampThinking(thinkingLevel))
                .putInt(K_RECOGNITION_MODE, recognitionMode == MODE_TRANSLATION
                        ? MODE_TRANSLATION : MODE_QA)
                .putInt(K_TRANSLATION_LANGUAGE, TranslationLanguage.clamp(translationLanguage))
                .putBoolean(K_USE_QUESTION_BANK, useQuestionBank)
                .putBoolean(K_USE_GLOSSARY, useGlossary)
                .apply();
        return new SecretStore(app).put(SECRET_API_KEY, apiKey == null ? "" : apiKey.trim());
    }

    public static int getDisplayMode(Context context) {
        return clampDisplayMode(prefs(context).getInt(K_DISPLAY_MODE, DISPLAY_LARGE));
    }

    public static void setDisplayMode(Context context, int mode) {
        prefs(context).edit().putInt(K_DISPLAY_MODE, clampDisplayMode(mode)).apply();
    }

    public static boolean isFlagSecureEnabled(Context context) {
        return prefs(context).getBoolean(K_FLAG_SECURE, true);
    }

    public static void setFlagSecureEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(K_FLAG_SECURE, enabled).apply();
    }

    public static void setCompactUserSearchMode(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(K_COMPACT_USER_SEARCH, enabled).apply();
    }

    public static void setThinkingLevel(Context context, int level) {
        prefs(context).edit().putInt(K_THINKING_LEVEL, clampThinking(level)).apply();
    }

    public static void setRecognitionMode(Context context, int mode) {
        prefs(context).edit().putInt(K_RECOGNITION_MODE,
                mode == MODE_TRANSLATION ? MODE_TRANSLATION : MODE_QA).apply();
    }

    public static void setTranslationLanguage(Context context, int language) {
        prefs(context).edit().putInt(K_TRANSLATION_LANGUAGE,
                TranslationLanguage.clamp(language)).apply();
    }

    public static void setUseQuestionBank(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(K_USE_QUESTION_BANK, enabled).apply();
    }

    public static void setUseGlossary(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(K_USE_GLOSSARY, enabled).apply();
    }

    public static boolean isCompactOverStatusBar(Context context) {
        return prefs(context).getBoolean(K_COMPACT_OVER_STATUS, false);
    }

    public static void setCompactOverStatusBar(Context context, boolean allowed) {
        prefs(context).edit().putBoolean(K_COMPACT_OVER_STATUS, allowed).apply();
    }

    private static int clampInterval(int seconds) {
        return Math.max(1, Math.min(300, seconds));
    }

    private static int clampThinking(int level) {
        return level < THINKING_OFF || level > THINKING_HIGH ? THINKING_OFF : level;
    }

    private static int clampDisplayMode(int mode) {
        return mode < DISPLAY_LARGE || mode > DISPLAY_NOTIFICATION ? DISPLAY_LARGE : mode;
    }

    public static void setA11yEverEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean(K_A11Y_EVER, value).apply();
    }

    public static boolean isA11yEverEnabled(Context context) {
        return prefs(context).getBoolean(K_A11Y_EVER, false);
    }

    public static void saveOverlayPosition(Context context, int x, int y) {
        prefs(context).edit().putInt(K_OVERLAY_X, x).putInt(K_OVERLAY_Y, y).apply();
    }

    public static int overlayX(Context context, int fallback) {
        return prefs(context).getInt(K_OVERLAY_X, fallback);
    }

    public static int overlayY(Context context, int fallback) {
        return prefs(context).getInt(K_OVERLAY_Y, fallback);
    }

    public static void saveCompactPosition(Context context, int x, int y) {
        prefs(context).edit().putInt(K_COMPACT_X, x).putInt(K_COMPACT_Y, y).apply();
    }

    public static int compactX(Context context, int fallback) {
        return prefs(context).getInt(K_COMPACT_X, fallback);
    }

    public static int compactY(Context context, int fallback) {
        return prefs(context).getInt(K_COMPACT_Y, fallback);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
