package com.moiclathy.screencoach.data;

/** Snapshot used by the main screen to render session state without binding. */
public final class CoachRuntime {
    public static final int STATE_IDLE = 0;
    public static final int STATE_SEARCHING = 1;
    public static final int STATE_KNOWN = 2;
    public static final int STATE_ERROR = 3;

    public static volatile boolean running;
    public static volatile String mode = "";
    public static volatile int questionNo = 1;
    public static volatile int state = STATE_IDLE;
    public static volatile String status = "";
    public static volatile int intervalSeconds = 3;
    public static volatile long updatedAt = 0L;

    public static void update(int newState, int newQuestionNo, String newStatus) {
        state = newState;
        questionNo = newQuestionNo;
        status = newStatus == null ? "" : newStatus;
        updatedAt = System.currentTimeMillis();
    }

    public static void reset() {
        running = false;
        mode = "";
        questionNo = 1;
        state = STATE_IDLE;
        status = "";
        intervalSeconds = 3;
        updatedAt = 0L;
    }
}
