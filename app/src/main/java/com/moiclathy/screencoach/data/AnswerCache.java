package com.moiclathy.screencoach.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Tiny SQLite cache. Every successful answer is written here for instant green re-display. */
public final class AnswerCache extends SQLiteOpenHelper {
    private static final String DB_NAME = "screencoach_answers.db";
    private static final int DB_VERSION = 1;
    private static volatile AnswerCache instance;

    public static AnswerCache get(Context context) {
        if (instance == null) {
            synchronized (AnswerCache.class) {
                if (instance == null) {
                    instance = new AnswerCache(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private AnswerCache(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE answers (" +
                "fingerprint TEXT PRIMARY KEY," +
                "image_hash TEXT," +
                "question_no INTEGER NOT NULL," +
                "answer TEXT NOT NULL," +
                "answer_text TEXT," +
                "confidence REAL NOT NULL DEFAULT 0," +
                "visible_text TEXT," +
                "created_at INTEGER NOT NULL," +
                "last_seen INTEGER NOT NULL," +
                "hits INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_answers_seen ON answers(last_seen)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS answers");
        onCreate(db);
    }

    public synchronized CachedAnswer find(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) return null;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("answers",
                new String[]{"fingerprint", "image_hash", "question_no", "answer", "answer_text",
                        "confidence", "created_at", "last_seen", "hits"},
                "fingerprint=?", new String[]{fingerprint}, null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) return null;
            CachedAnswer answer = read(cursor);
            ContentValues touch = new ContentValues();
            touch.put("last_seen", System.currentTimeMillis());
            touch.put("hits", answer.hits + 1);
            db.update("answers", touch, "fingerprint=?", new String[]{fingerprint});
            return answer;
        } finally {
            cursor.close();
        }
    }

    public synchronized void upsert(String fingerprint, String imageHash, int questionNo,
                                    String answer, String answerText, double confidence,
                                    String visibleText) {
        if (fingerprint == null || fingerprint.isEmpty() || answer == null) return;
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("fingerprint", fingerprint);
        values.put("image_hash", imageHash);
        values.put("question_no", questionNo);
        values.put("answer", answer);
        values.put("answer_text", answerText == null ? "" : answerText);
        values.put("confidence", confidence);
        values.put("visible_text", truncate(visibleText, 4000));
        values.put("created_at", now);
        values.put("last_seen", now);
        SQLiteDatabase db = getWritableDatabase();
        int updated = db.update("answers", values, "fingerprint=?", new String[]{fingerprint});
        if (updated == 0) {
            values.put("hits", 0);
            db.insertWithOnConflict("answers", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    public synchronized int count() {
        Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM answers", null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public synchronized CachedAnswer latest() {
        Cursor cursor = getReadableDatabase().query("answers",
                new String[]{"fingerprint", "image_hash", "question_no", "answer", "answer_text",
                        "confidence", "created_at", "last_seen", "hits"},
                null, null, null, null, "last_seen DESC", "1");
        try {
            return cursor.moveToFirst() ? read(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public synchronized int clear() {
        return getWritableDatabase().delete("answers", null, null);
    }

    public synchronized int prune(long maxAgeMs) {
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        return getWritableDatabase().delete("answers", "last_seen < ?",
                new String[]{String.valueOf(cutoff)});
    }

    private static CachedAnswer read(Cursor cursor) {
        return new CachedAnswer(
                cursor.getString(0),
                cursor.isNull(1) ? "" : cursor.getString(1),
                cursor.getInt(2),
                cursor.getString(3),
                cursor.isNull(4) ? "" : cursor.getString(4),
                cursor.getDouble(5),
                cursor.getLong(6),
                cursor.getLong(7),
                cursor.getInt(8));
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
