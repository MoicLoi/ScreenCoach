package com.moiclathy.screencoach.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * Resident-notification output. There is no overlay in this mode. The compact
 * answer line rotates through known answers every 4 seconds; only the question
 * number, answer and a colored status square are shown. Reasoning/answer_text is
 * never included.
 */
public final class NotificationPresenter implements AnswerPresenter {
    private static final long ROTATE_MS = 4000L;
    private static final int COLOR_YELLOW = 0xFFFBBF24;
    private static final int COLOR_GREEN = 0xFF22C55E;
    private static final int COLOR_RED = 0xFFEF4444;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Item> deck = new ArrayList<>();
    private int index = -1;
    private int stateColor = COLOR_YELLOW;
    private int fallbackQuestionNo = 1;
    private boolean started = false;
    private boolean hasRendered = false;

    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (!started) return;
            if (!deck.isEmpty()) index = (index + 1) % deck.size();
            updateNotification();
            mainHandler.postDelayed(this, ROTATE_MS);
        }
    };

    public NotificationPresenter(Context context) {
        this.context = context;
    }

    @Override
    public void show() {
        started = true;
        mainHandler.removeCallbacks(rotate);
        mainHandler.postDelayed(rotate, ROTATE_MS);
    }

    @Override
    public void showSearching(int questionNo, String status) {
        fallbackQuestionNo = Math.max(0, questionNo);
        stateColor = COLOR_YELLOW;
        // The 4-second ticker owns routine refresh; only render the first state
        // immediately so the foreground notification is not rewritten every 3s.
        if (!hasRendered) {
            hasRendered = true;
            updateNotification();
        }
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
        hasRendered = true;
        updateNotification();
    }

    @Override
    public void showError(int failedQuestionNo, String message) {
        fallbackQuestionNo = Math.max(0, failedQuestionNo);
        stateColor = COLOR_RED;
        hasRendered = true;
        updateNotification();
    }

    @Override
    public void updateSearchingStatus(String status) {
        // Notification mode deliberately shows no progress text.
    }

    @Override
    public void setSecure(boolean secure) {
        // No overlay window in this mode.
    }

    @Override
    public void remove() {
        started = false;
        mainHandler.removeCallbacks(rotate);
    }

    private void updateNotification() {
        Item item = currentItem();
        String body;
        if (item == null) {
            body = fallbackQuestionNo > 0 ? fallbackQuestionNo + "?" : "?";
        } else {
            body = item.questionNo > 0 ? item.questionNo + item.answer : item.answer;
        }
        String square;
        if (stateColor == COLOR_GREEN) square = "\uD83D\uDFE9";       // 🟩
        else if (stateColor == COLOR_RED) square = "\uD83D\uDFE5";   // 🟥
        else square = "\uD83D\uDFE8";                                 // 🟨
        NotificationHelper.updateSession(context, body + " " + square);
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
