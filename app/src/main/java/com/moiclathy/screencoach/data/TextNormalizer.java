package com.moiclathy.screencoach.data;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Normalization and lightweight bigram similarity for local bank/glossary matching. */
public final class TextNormalizer {
    private TextNormalizer() { }

    public static String normalize(String text) {
        if (text == null) return "";
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); ) {
            int cp = lower.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) out.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    public static double dice(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        if (x.isEmpty() || y.isEmpty()) return 0.0;
        if (x.equals(y)) return 1.0;
        if (x.length() < 2 || y.length() < 2) return 0.0;
        Set<String> left = bigrams(x);
        Set<String> right = bigrams(y);
        int common = 0;
        for (String gram : left) {
            if (right.contains(gram)) common++;
        }
        return (2.0 * common) / (left.size() + right.size());
    }

    private static Set<String> bigrams(String value) {
        Set<String> grams = new HashSet<>();
        for (int i = 0; i + 1 < value.length(); i++) {
            grams.add(value.substring(i, i + 2));
        }
        return grams;
    }
}
