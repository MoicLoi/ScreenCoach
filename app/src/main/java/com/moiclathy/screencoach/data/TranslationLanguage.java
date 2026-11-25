package com.moiclathy.screencoach.data;

/** Selectable translation directions shown in the secondary language menu. */
public final class TranslationLanguage {
    public static final int ZH_EN_BIDIRECTIONAL = 0;

    private static final String[] LABELS = {
            "中 ⇄ 英（默认双向）",
            "中文 → English",
            "English → 中文",
            "中文 → 日本語",
            "日本語 → 中文",
            "中文 → 한국어",
            "한국어 → 中文",
            "中文 → Français",
            "Français → 中文",
            "中文 → Deutsch",
            "Deutsch → 中文",
            "中文 → Español",
            "Español → 中文",
            "English → 日本語",
            "日本語 → English"
    };

    private static final String[] PROMPTS = {
            "自动判断当前页面的主要语言：中文翻译成英文，英文翻译成中文。只输出最终译文。",
            "把当前页面的中文翻译成英文。只输出最终译文。",
            "把当前页面的英文翻译成中文。只输出最终译文。",
            "把当前页面的中文翻译成日文。只输出最终译文。",
            "把当前页面的日文翻译成中文。只输出最终译文。",
            "把当前页面的中文翻译成韩文。只输出最终译文。",
            "把当前页面的韩文翻译成中文。只输出最终译文。",
            "把当前页面的中文翻译成法文。只输出最终译文。",
            "把当前页面的法文翻译成中文。只输出最终译文。",
            "把当前页面的中文翻译成德文。只输出最终译文。",
            "把当前页面的德文翻译成中文。只输出最终译文。",
            "把当前页面的中文翻译成西班牙文。只输出最终译文。",
            "把当前页面的西班牙文翻译成中文。只输出最终译文。",
            "把当前页面的英文翻译成日文。只输出最终译文。",
            "把当前页面的日文翻译成英文。只输出最终译文。"
    };

    private TranslationLanguage() { }

    public static int count() {
        return LABELS.length;
    }

    public static int clamp(int value) {
        return value < 0 || value >= LABELS.length ? ZH_EN_BIDIRECTIONAL : value;
    }

    public static String label(int value) {
        return LABELS[clamp(value)];
    }

    public static String prompt(int value) {
        return PROMPTS[clamp(value)];
    }

    public static boolean isBidirectional(int value) {
        return clamp(value) == ZH_EN_BIDIRECTIONAL;
    }
}
