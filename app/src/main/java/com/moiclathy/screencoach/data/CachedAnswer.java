package com.moiclathy.screencoach.data;

/** One persisted answer row. The local database is a short-lived cache, not a question bank. */
public final class CachedAnswer {
    public final String fingerprint;
    public final String imageHash;
    public final int questionNo;
    public final String answer;
    public final String answerText;
    public final double confidence;
    public final long createdAt;
    public final long lastSeen;
    public final int hits;

    public CachedAnswer(String fingerprint, String imageHash, int questionNo, String answer,
                        String answerText, double confidence, long createdAt, long lastSeen, int hits) {
        this.fingerprint = fingerprint;
        this.imageHash = imageHash;
        this.questionNo = questionNo;
        this.answer = answer;
        this.answerText = answerText;
        this.confidence = confidence;
        this.createdAt = createdAt;
        this.lastSeen = lastSeen;
        this.hits = hits;
    }
}
