package com.moiclathy.screencoach.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Visible accessibility text plus a best-effort question number. */
public final class ScreenSnapshot {
    public static final ScreenSnapshot EMPTY = new ScreenSnapshot("", "", -1);

    public final String packageName;
    public final String text;
    public final int questionNo;

    public ScreenSnapshot(String packageName, String text, int questionNo) {
        this.packageName = packageName == null ? "" : packageName;
        this.text = text == null ? "" : text;
        this.questionNo = questionNo;
    }

    private static final Pattern[] QUESTION_PATTERNS = new Pattern[]{
            Pattern.compile("第\\s*([0-9]{1,3})\\s*题"),
            Pattern.compile("(?i)question\\s*([0-9]{1,3})"),
            Pattern.compile("(?m)^\\s*([0-9]{1,3})\\s*[.、．)）]\\s+"),
            Pattern.compile("\\b([0-9]{1,3})\\s*/\\s*[0-9]{1,3}\\b")
    };

    public static int detectQuestionNo(String text) {
        if (text == null || text.isEmpty()) return -1;
        for (Pattern pattern : QUESTION_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                try {
                    int number = Integer.parseInt(matcher.group(1));
                    if (number >= 1 && number <= 999) return number;
                } catch (NumberFormatException ignored) { }
            }
        }
        return -1;
    }
}
