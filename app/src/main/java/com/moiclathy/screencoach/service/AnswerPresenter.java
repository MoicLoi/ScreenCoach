package com.moiclathy.screencoach.service;

/** Output abstraction for large overlay, compact overlay and notification mode. */
public interface AnswerPresenter {
    void show();

    void showSearching(int questionNo, String status);

    void showAnswer(int questionNo, String answer, String detail, boolean cached);

    void showError(int failedQuestionNo, String message);

    void updateSearchingStatus(String status);

    void setSecure(boolean secure);

    void remove();
}
