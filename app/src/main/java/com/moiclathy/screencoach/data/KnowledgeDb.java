package com.moiclathy.screencoach.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Persistent imported question bank and translation glossary. */
public final class KnowledgeDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "screencoach_knowledge.db";
    private static final int DB_VERSION = 1;
    private static volatile KnowledgeDb instance;

    public static KnowledgeDb get(Context context) {
        if (instance == null) {
            synchronized (KnowledgeDb.class) {
                if (instance == null) instance = new KnowledgeDb(context.getApplicationContext());
            }
        }
        return instance;
    }

    private KnowledgeDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE questions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "question TEXT NOT NULL," +
                "normalized TEXT NOT NULL," +
                "answer TEXT NOT NULL," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX idx_questions_norm ON questions(normalized)");
        db.execSQL("CREATE TABLE glossary (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "source TEXT NOT NULL," +
                "target TEXT NOT NULL," +
                "normalized_source TEXT NOT NULL," +
                "normalized_target TEXT NOT NULL," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX idx_glossary_terms ON glossary(normalized_source, normalized_target)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS questions");
        db.execSQL("DROP TABLE IF EXISTS glossary");
        onCreate(db);
    }

    public synchronized int addQuestions(List<Question> questions) {
        if (questions == null || questions.isEmpty()) return 0;
        SQLiteDatabase db = getWritableDatabase();
        int count = 0;
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            for (Question q : questions) {
                if (q == null || q.question == null || q.answer == null) continue;
                String text = q.question.trim();
                String answer = q.answer.trim();
                String normalized = TextNormalizer.normalize(text);
                if (text.isEmpty() || answer.isEmpty() || normalized.isEmpty()) continue;
                ContentValues values = new ContentValues();
                values.put("question", text);
                values.put("normalized", normalized);
                values.put("answer", answer);
                values.put("created_at", now);
                db.insertWithOnConflict("questions", null, values,
                        SQLiteDatabase.CONFLICT_REPLACE);
                count++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return count;
    }

    public synchronized int addGlossary(List<Glossary> entries) {
        if (entries == null || entries.isEmpty()) return 0;
        SQLiteDatabase db = getWritableDatabase();
        int count = 0;
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            for (Glossary g : entries) {
                if (g == null || g.source == null || g.target == null) continue;
                String source = g.source.trim();
                String target = g.target.trim();
                String ns = TextNormalizer.normalize(source);
                String nt = TextNormalizer.normalize(target);
                if (source.isEmpty() || target.isEmpty() || ns.isEmpty() || nt.isEmpty()) continue;
                ContentValues values = new ContentValues();
                values.put("source", source);
                values.put("target", target);
                values.put("normalized_source", ns);
                values.put("normalized_target", nt);
                values.put("created_at", now);
                db.insertWithOnConflict("glossary", null, values,
                        SQLiteDatabase.CONFLICT_REPLACE);
                count++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return count;
    }

    public synchronized List<Question> questions(int limit) {
        List<Question> result = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query("questions",
                new String[]{"question", "answer"}, null, null, null, null,
                "id DESC", String.valueOf(Math.max(1, limit)));
        try {
            while (cursor.moveToNext()) {
                result.add(new Question(cursor.getString(0), cursor.getString(1)));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    public synchronized List<Glossary> glossary(int limit) {
        List<Glossary> result = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query("glossary",
                new String[]{"source", "target"}, null, null, null, null,
                "id DESC", String.valueOf(Math.max(1, limit)));
        try {
            while (cursor.moveToNext()) {
                result.add(new Glossary(cursor.getString(0), cursor.getString(1)));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    public synchronized int questionCount() {
        return count("questions");
    }

    public synchronized int glossaryCount() {
        return count("glossary");
    }

    private int count(String table) {
        Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + table, null);
        try { return cursor.moveToFirst() ? cursor.getInt(0) : 0; }
        finally { cursor.close(); }
    }

    public synchronized void clearQuestions() {
        getWritableDatabase().delete("questions", null, null);
    }

    public synchronized void clearGlossary() {
        getWritableDatabase().delete("glossary", null, null);
    }

    public static final class Question {
        public final String question;
        public final String answer;
        public Question(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
    }

    public static final class Glossary {
        public final String source;
        public final String target;
        public Glossary(String source, String target) {
            this.source = source;
            this.target = target;
        }
    }
}
