package com.moiclathy.screencoach.data;

import java.util.ArrayList;
import java.util.List;

/** In-memory matcher over imported rows (bounded to 2,000 entries per table). */
public final class KnowledgeMatcher {
    private static final int LIMIT = 2000;
    private static final double QUESTION_THRESHOLD = 0.84;

    private final List<KnowledgeDb.Question> questions;
    private final List<KnowledgeDb.Glossary> glossary;

    public KnowledgeMatcher(KnowledgeDb db) {
        questions = new ArrayList<>(db.questions(LIMIT));
        glossary = new ArrayList<>(db.glossary(LIMIT));
    }

    public Match matchQuestion(String visibleText) {
        String screen = TextNormalizer.normalize(visibleText);
        if (screen.length() < 4) return null;
        String[] lines = visibleText == null ? new String[0] : visibleText.split("\\r?\\n");
        Match best = null;
        double bestScore = 0.0;
        for (KnowledgeDb.Question item : questions) {
            String question = TextNormalizer.normalize(item.question);
            if (question.length() < 5) continue;
            double score = 0.0;
            if (screen.contains(question)) {
                score = 1.0;
            } else {
                for (String line : lines) {
                    String normalized = TextNormalizer.normalize(line);
                    if (normalized.length() < 4) continue;
                    score = Math.max(score, TextNormalizer.dice(normalized, question));
                }
                score = Math.max(score, TextNormalizer.dice(screen, question) * 0.92);
            }
            if (score > bestScore) {
                bestScore = score;
                if (score >= QUESTION_THRESHOLD) {
                    best = new Match(item.answer, "来自题库");
                }
            }
        }
        return best;
    }

    public Match matchGlossary(String visibleText, boolean bidirectional) {
        String screen = TextNormalizer.normalize(visibleText);
        if (screen.length() < 2) return null;
        String bestAnswer = null;
        int bestLength = 0;
        for (KnowledgeDb.Glossary item : glossary) {
            String source = TextNormalizer.normalize(item.source);
            if (source.length() >= 2 && screen.contains(source)
                    && source.length() > bestLength) {
                bestLength = source.length();
                bestAnswer = item.target;
            }
            if (bidirectional) {
                String target = TextNormalizer.normalize(item.target);
                if (target.length() >= 2 && screen.contains(target)
                        && target.length() > bestLength) {
                    bestLength = target.length();
                    bestAnswer = item.source;
                }
            }
        }
        return bestAnswer == null ? null : new Match(bestAnswer, "来自翻译词库");
    }

    public static final class Match {
        public final String answer;
        public final String detail;
        public Match(String answer, String detail) {
            this.answer = answer;
            this.detail = detail;
        }
    }
}
