package com.moiclathy.screencoach.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses question-bank and translation-glossary uploads (JSON/CSV/TSV/TXT). */
public final class ImportParser {
    private static final int MAX_ITEMS = 20000;

    private ImportParser() { }

    public static List<KnowledgeDb.Question> parseQuestions(String content, String filename) {
        content = stripBom(content);
        List<KnowledgeDb.Question> result = new ArrayList<>();
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".json") || content.trim().startsWith("[")
                || content.trim().startsWith("{")) {
            parseQuestionJson(content, result);
        } else {
            for (String[] row : rows(content)) {
                if (row.length < 2) continue;
                String question = clean(row[0]);
                String answer = clean(row[1]);
                if (isQuestionHeader(question, answer)) continue;
                if (!question.isEmpty() && !answer.isEmpty()) {
                    result.add(new KnowledgeDb.Question(question, answer));
                }
                if (result.size() >= MAX_ITEMS) break;
            }
        }
        return result;
    }

    public static List<KnowledgeDb.Glossary> parseGlossary(String content, String filename) {
        content = stripBom(content);
        List<KnowledgeDb.Glossary> result = new ArrayList<>();
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".json") || content.trim().startsWith("[")
                || content.trim().startsWith("{")) {
            parseGlossaryJson(content, result);
        } else {
            for (String[] row : rows(content)) {
                if (row.length < 2) continue;
                String source = clean(row[0]);
                String target = clean(row[1]);
                if (isGlossaryHeader(source, target)) continue;
                if (!source.isEmpty() && !target.isEmpty()) {
                    result.add(new KnowledgeDb.Glossary(source, target));
                }
                if (result.size() >= MAX_ITEMS) break;
            }
        }
        return result;
    }

    private static void parseQuestionJson(String content, List<KnowledgeDb.Question> result) {
        try {
            Object root = new org.json.JSONTokener(content).nextValue();
            JSONArray array = root instanceof JSONArray ? (JSONArray) root : null;
            if (root instanceof JSONObject) {
                JSONObject object = (JSONObject) root;
                array = object.optJSONArray("items");
                if (array == null) array = object.optJSONArray("questions");
                if (array == null) array = object.optJSONArray("data");
            }
            if (array == null) return;
            for (int i = 0; i < array.length() && result.size() < MAX_ITEMS; i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String question = first(item, "question", "q", "题目", "问题");
                String answer = first(item, "answer", "a", "答案");
                if (!question.isEmpty() && !answer.isEmpty()) {
                    result.add(new KnowledgeDb.Question(question, answer));
                }
            }
        } catch (Exception ignored) { }
    }

    private static void parseGlossaryJson(String content, List<KnowledgeDb.Glossary> result) {
        try {
            Object root = new org.json.JSONTokener(content).nextValue();
            JSONArray array = root instanceof JSONArray ? (JSONArray) root : null;
            if (root instanceof JSONObject) {
                JSONObject object = (JSONObject) root;
                array = object.optJSONArray("items");
                if (array == null) array = object.optJSONArray("glossary");
                if (array == null) array = object.optJSONArray("terms");
                if (array == null) array = object.optJSONArray("data");
            }
            if (array == null) return;
            for (int i = 0; i < array.length() && result.size() < MAX_ITEMS; i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String source = first(item, "source", "src", "term", "原文", "词条");
                String target = first(item, "target", "dst", "translation", "译文", "翻译");
                if (!source.isEmpty() && !target.isEmpty()) {
                    result.add(new KnowledgeDb.Glossary(source, target));
                }
            }
        } catch (Exception ignored) { }
    }

    private static List<String[]> rows(String content) {
        List<String[]> rows = new ArrayList<>();
        if (content == null) return rows;
        String delimiter = content.contains("\t") ? "\t" : ",";
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            List<String> fields = splitDelimited(line, delimiter);
            if (fields.size() < 2 && delimiter.equals(",")) {
                fields = splitByArrow(line);
            }
            if (fields.size() >= 2) rows.add(fields.toArray(new String[0]));
            if (rows.size() >= MAX_ITEMS) break;
        }
        return rows;
    }

    private static List<String> splitDelimited(String line, String delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted && line.startsWith(delimiter, i)) {
                fields.add(field.toString().trim());
                field.setLength(0);
                i += delimiter.length() - 1;
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString().trim());
        return fields;
    }

    private static List<String> splitByArrow(String line) {
        String[] separators = {"=>", "->", "|", "::"};
        for (String separator : separators) {
            int index = line.indexOf(separator);
            if (index > 0) {
                List<String> fields = new ArrayList<>();
                fields.add(line.substring(0, index).trim());
                fields.add(line.substring(index + separator.length()).trim());
                return fields;
            }
        }
        return new ArrayList<>();
    }

    private static boolean isQuestionHeader(String a, String b) {
        String x = a.toLowerCase(Locale.ROOT).replace(" ", "");
        String y = b.toLowerCase(Locale.ROOT).replace(" ", "");
        return (x.equals("question") || x.equals("q") || x.equals("题目") || x.equals("问题"))
                && (y.equals("answer") || y.equals("a") || y.equals("答案"));
    }

    private static boolean isGlossaryHeader(String a, String b) {
        String x = a.toLowerCase(Locale.ROOT).replace(" ", "");
        String y = b.toLowerCase(Locale.ROOT).replace(" ", "");
        return (x.equals("source") || x.equals("src") || x.equals("term") || x.equals("原文") || x.equals("词条"))
                && (y.equals("target") || y.equals("dst") || y.equals("translation") || y.equals("译文") || y.equals("翻译"));
    }

    private static String first(JSONObject item, String... keys) {
        for (String key : keys) {
            String value = item.optString(key, "");
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripBom(String content) {
        if (content != null && content.startsWith("\uFEFF")) return content.substring(1);
        return content == null ? "" : content;
    }
}
