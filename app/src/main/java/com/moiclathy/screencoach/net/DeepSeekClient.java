package com.moiclathy.screencoach.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.moiclathy.screencoach.data.KnowledgeDb;

/**
 * The only model transport used by ScreenCoach: DeepSeek's official
 * OpenAI-compatible API. There is deliberately no configurable endpoint.
 *
 * Vision requests use model=deepseek-flash and the documented
 * image_url/data: URL content block.
 */
public final class DeepSeekClient {
    private static final String TAG = "DeepSeekClient";
    public static final String API_BASE = "https://api.deepseek.com";
    public static final String CHAT_URL = API_BASE + "/chat/completions";
    public static final String MODELS_URL = API_BASE + "/models";
    public static final String VISION_MODEL = "deepseek-flash";
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    public enum ErrorKind { CONFIG, NETWORK, AUTH, HTTP, PROTOCOL, CANCELLED }

    public static final class DeepSeekException extends Exception {
        public final ErrorKind kind;
        public DeepSeekException(ErrorKind kind, String message) {
            super(message);
            this.kind = kind;
        }
        public DeepSeekException(ErrorKind kind, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
        }
    }

    public static final class Answer {
        public final int questionNo;
        public final String answer;
        public final String answerText;
        public final double confidence;
        public final boolean allKnown;

        public Answer(int questionNo, String answer, String answerText,
                      double confidence, boolean allKnown) {
            this.questionNo = questionNo;
            this.answer = answer;
            this.answerText = answerText;
            this.confidence = confidence;
            this.allKnown = allKnown;
        }
    }

    private final String apiKey;
    private final int thinkingLevel;
    private volatile HttpURLConnection currentConnection;

    public DeepSeekClient(String apiKey) {
        this(apiKey, com.moiclathy.screencoach.data.AppSettings.THINKING_OFF);
    }

    public DeepSeekClient(String apiKey, int thinkingLevel) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.thinkingLevel = thinkingLevel;
    }

    /** Verifies the key and that the official vision model is visible to it. */
    public List<String> listModels() throws DeepSeekException {
        requireKey();
        HttpResult response = request("GET", MODELS_URL, null);
        if (response.code == 401 || response.code == 403) {
            throw new DeepSeekException(ErrorKind.AUTH, "DeepSeek API Key 无效或无权限");
        }
        if (response.code < 200 || response.code >= 300) {
            throw new DeepSeekException(ErrorKind.HTTP,
                    "DeepSeek 模型列表请求失败 (HTTP " + response.code + ")");
        }
        try {
            JSONArray data = new JSONObject(response.body).optJSONArray("data");
            List<String> models = new ArrayList<>();
            if (data != null) {
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.optJSONObject(i);
                    if (item != null) {
                        String id = item.optString("id", "");
                        if (!id.isEmpty()) models.add(id);
                    }
                }
            }
            return models;
        } catch (Exception e) {
            throw new DeepSeekException(ErrorKind.PROTOCOL, "DeepSeek 模型列表格式异常", e);
        }
    }

    public Answer solve(String imageBase64, String mimeType, int questionNo,
                        String visibleText, String packageName) throws DeepSeekException {
        requireKey();
        if (imageBase64 == null || imageBase64.isEmpty()) {
            throw new DeepSeekException(ErrorKind.CONFIG, "没有可发送的截图");
        }
        JSONObject request = buildRequest(imageBase64, mimeType, questionNo, visibleText, packageName);
        HttpResult response = request("POST", CHAT_URL, request.toString());
        if ((response.code == 400 || response.code == 422)
                && response.body.contains("response_format")) {
            request.remove("response_format");
            response = request("POST", CHAT_URL, request.toString());
        }
        if (response.code == 401 || response.code == 403) {
            throw new DeepSeekException(ErrorKind.AUTH, "DeepSeek API Key 无效或无权限");
        }
        if (response.code == 429) {
            throw new DeepSeekException(ErrorKind.HTTP, "DeepSeek 请求频率受限，请稍后重试");
        }
        if (response.code < 200 || response.code >= 300) {
            throw new DeepSeekException(ErrorKind.HTTP,
                    "DeepSeek API HTTP " + response.code + "：" + sanitize(errorMessage(response.body), 180));
        }
        String content = extractContent(response.body);
        if (content.trim().isEmpty()) {
            throw new DeepSeekException(ErrorKind.PROTOCOL, "DeepSeek 返回了空内容，请稍后重试");
        }
        JSONObject payload = extractJsonObject(content);
        if (payload == null) {
            throw new DeepSeekException(ErrorKind.PROTOCOL,
                    "DeepSeek 返回内容不是约定 JSON（status/answer）");
        }
        return parseAnswer(payload, questionNo);
    }

    /** Translation mode: translate the current page; returns questionNo=0. */
    public Answer translate(String imageBase64, String mimeType, String visibleText,
                            String packageName, String languagePrompt) throws DeepSeekException {
        requireKey();
        if (imageBase64 == null || imageBase64.isEmpty()) {
            throw new DeepSeekException(ErrorKind.CONFIG, "没有可发送的截图");
        }
        String system = "你是屏学助手的翻译模块。请只输出一个 json 对象，不要输出 Markdown。"
                + "字段：{\"status\":\"ok|unknown|error\",\"translation\":\"最终译文\","
                + "\"source_language\":\"...\",\"target_language\":\"...\","
                + "\"confidence\":0.0,\"message\":\"仅错误时填写\"}。"
                + "只翻译当前页面的主要内容，保留数字、公式和专有名词。看不清或没有可翻译文本时 status 为 unknown。";
        String user = (languagePrompt == null ? "" : languagePrompt)
                + "\n当前应用包名：" + (packageName == null ? "" : packageName)
                + "\n屏幕可访问文本（可能不完整）：\n"
                + (visibleText == null || visibleText.isEmpty() ? "（无）" : visibleText)
                + "\n\n请翻译截图/文本，并严格按 json 返回。";
        JSONObject request = buildChatWithImage(imageBase64, mimeType, system, user, 4096);
        String content = requestChat(request);
        JSONObject payload = extractJsonObject(content);
        if (payload == null) {
            throw new DeepSeekException(ErrorKind.PROTOCOL,
                    "DeepSeek 返回内容不是约定翻译 JSON");
        }
        String status = payload.optString("status", "ok").toLowerCase(Locale.ROOT);
        if ("error".equals(status) || "unknown".equals(status)) {
            String msg = payload.optString("message", "无法从当前页面识别可翻译文本");
            throw new DeepSeekException(ErrorKind.PROTOCOL, sanitize(msg, 180));
        }
        String translation = valueToString(payload.opt("translation"));
        if (translation.trim().isEmpty()) {
            throw new DeepSeekException(ErrorKind.PROTOCOL, "DeepSeek 返回中没有 translation 字段");
        }
        if (translation.length() > 4000) translation = translation.substring(0, 4000);
        double confidence = payload.optDouble("confidence", 0.0);
        if (Double.isNaN(confidence) || Double.isInfinite(confidence)) confidence = 0.0;
        return new Answer(0, translation.trim(), "", Math.max(0, Math.min(1, confidence)), false);
    }

    /** Used by photo import: ask the vision model to extract Q&A pairs from an image. */
    public List<KnowledgeDb.Question> extractQuestionsFromImage(String imageBase64, String mimeType)
            throws DeepSeekException {
        requireKey();
        if (imageBase64 == null || imageBase64.isEmpty()) {
            throw new DeepSeekException(ErrorKind.CONFIG, "没有可解析的图片");
        }
        String system = "你是题库整理工具。识别图片中的题目和答案，只输出 json 对象，不要输出 Markdown。"
                + "格式：{\"items\":[{\"question\":\"题干\",\"answer\":\"答案\"}]}。"
                + "选择题答案尽量只保留选项字母；看不清的条目不要编造。";
        String user = "请提取截图中所有可辨识的题目和答案，按 json 返回。";
        JSONObject request = buildChatWithImage(imageBase64, mimeType, system, user, 4096);
        String content = requestChat(request);
        JSONObject payload = extractJsonObject(content);
        JSONArray items = null;
        if (payload != null) {
            items = payload.optJSONArray("items");
            if (items == null) items = payload.optJSONArray("questions");
            if (items == null) items = payload.optJSONArray("data");
        }
        if (items == null) {
            try { items = new JSONArray(content.trim()); } catch (Exception ignored) { }
        }
        if (items == null) {
            throw new DeepSeekException(ErrorKind.PROTOCOL, "题库图片解析缺少 items 数组");
        }
        List<KnowledgeDb.Question> result = new ArrayList<>();
        for (int i = 0; i < items.length() && result.size() < 200; i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String question = item.optString("question", item.optString("q", "")).trim();
            String answer = item.optString("answer", item.optString("a", "")).trim();
            if (!question.isEmpty() && !answer.isEmpty()) {
                result.add(new KnowledgeDb.Question(question, answer));
            }
        }
        return result;
    }

    private JSONObject buildChatWithImage(String imageBase64, String mimeType,
                                          String systemPrompt, String userText, int maxTokens) {
        try {
            JSONObject userTextBlock = new JSONObject();
            userTextBlock.put("type", "text");
            userTextBlock.put("text", userText == null ? "" : userText);

            JSONObject imageUrl = new JSONObject();
            imageUrl.put("url", "data:" + (mimeType == null ? "image/jpeg" : mimeType)
                    + ";base64," + imageBase64);
            imageUrl.put("detail", "high");
            JSONObject imageBlock = new JSONObject();
            imageBlock.put("type", "image_url");
            imageBlock.put("image_url", imageUrl);

            JSONArray content = new JSONArray();
            content.put(userTextBlock);
            content.put(imageBlock);

            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", content);
            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", systemPrompt);
            JSONArray messages = new JSONArray();
            messages.put(system);
            messages.put(user);

            JSONObject request = new JSONObject();
            request.put("model", VISION_MODEL);
            request.put("messages", messages);
            request.put("temperature", 0);
            request.put("max_tokens", maxTokens);
            request.put("stream", false);
            request.put("response_format", new JSONObject().put("type", "json_object"));
            if (thinkingLevel == com.moiclathy.screencoach.data.AppSettings.THINKING_OFF) {
                request.put("thinking", new JSONObject().put("type", "disabled"));
            } else {
                request.put("thinking", new JSONObject().put("type", "enabled"));
                request.put("reasoning_effort",
                        thinkingLevel == com.moiclathy.screencoach.data.AppSettings.THINKING_LOW
                                ? "low" : "high");
            }
            return request;
        } catch (Exception e) {
            throw new IllegalStateException("无法构造 DeepSeek 请求", e);
        }
    }

    private String requestChat(JSONObject request) throws DeepSeekException {
        HttpResult response = request("POST", CHAT_URL, request.toString());
        if ((response.code == 400 || response.code == 422)
                && response.body.contains("response_format")) {
            request.remove("response_format");
            response = request("POST", CHAT_URL, request.toString());
        }
        if (response.code == 401 || response.code == 403) {
            throw new DeepSeekException(ErrorKind.AUTH, "DeepSeek API Key 无效或无权限");
        }
        if (response.code == 429) {
            throw new DeepSeekException(ErrorKind.HTTP, "DeepSeek 请求频率受限，请稍后重试");
        }
        if (response.code < 200 || response.code >= 300) {
            throw new DeepSeekException(ErrorKind.HTTP,
                    "DeepSeek API HTTP " + response.code + "："
                            + sanitize(errorMessage(response.body), 180));
        }
        String content = extractContent(response.body);
        if (content.trim().isEmpty()) {
            throw new DeepSeekException(ErrorKind.PROTOCOL, "DeepSeek 返回了空内容，请稍后重试");
        }
        return content;
    }

    public void cancel() {
        HttpURLConnection connection = currentConnection;
        if (connection != null) {
            try { connection.disconnect(); } catch (Throwable ignored) { }
        }
    }

    private void requireKey() throws DeepSeekException {
        if (apiKey.isEmpty()) {
            throw new DeepSeekException(ErrorKind.CONFIG, "请先在首页填写 DeepSeek API Key");
        }
    }

    private JSONObject buildRequest(String imageBase64, String mimeType, int questionNo,
                                    String visibleText, String packageName) {
        try {
            String systemPrompt = "你是屏学助手的题目识读模块，只辅助个人学习。"
                    + "请只输出一个 json 对象，不要输出 Markdown。"
                    + "输出必须是 json，字段如下："
                    + "{\"status\":\"ok|unknown|error\",\"question_no\":1,"
                    + "\"answer\":\"选择题字母或简洁答案\",\"answer_text\":\"简短解析\","
                    + "\"confidence\":0.0,\"all_known\":false,\"message\":\"仅错误时填写\"}。"
                    + "看不清、截图无关或不是题目时 status 必须为 unknown。"
                    + "选择题 answer 只给字母（多选如 AC），填空/解答给简洁答案。"
                    + "不要编造，不要输出 API Key 或无关隐私。";

            JSONObject userText = new JSONObject();
            userText.put("type", "text");
            userText.put("text", "题号提示：" + questionNo
                    + "\n当前应用包名：" + (packageName == null ? "" : packageName)
                    + "\n屏幕可访问文本（可能不完整）：\n"
                    + (visibleText == null || visibleText.isEmpty() ? "（无）" : visibleText)
                    + "\n\n请识别当前题目，并严格按 json 约定返回。");

            JSONObject imageUrl = new JSONObject();
            imageUrl.put("url", "data:" + (mimeType == null ? "image/jpeg" : mimeType)
                    + ";base64," + imageBase64);
            imageUrl.put("detail", "high");
            JSONObject imageBlock = new JSONObject();
            imageBlock.put("type", "image_url");
            imageBlock.put("image_url", imageUrl);

            JSONArray content = new JSONArray();
            content.put(userText);
            content.put(imageBlock);

            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", content);
            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", systemPrompt);

            JSONArray messages = new JSONArray();
            messages.put(system);
            messages.put(user);

            JSONObject request = new JSONObject();
            request.put("model", VISION_MODEL);
            request.put("messages", messages);
            request.put("temperature", 0);
            request.put("max_tokens",
                    thinkingLevel == com.moiclathy.screencoach.data.AppSettings.THINKING_OFF
                            ? 1200 : 4096);
            request.put("stream", false);
            request.put("response_format", new JSONObject().put("type", "json_object"));
            if (thinkingLevel == com.moiclathy.screencoach.data.AppSettings.THINKING_OFF) {
                request.put("thinking", new JSONObject().put("type", "disabled"));
            } else {
                request.put("thinking", new JSONObject().put("type", "enabled"));
                request.put("reasoning_effort",
                        thinkingLevel == com.moiclathy.screencoach.data.AppSettings.THINKING_LOW
                                ? "low" : "high");
            }
            return request;
        } catch (Exception e) {
            throw new IllegalStateException("无法构造 DeepSeek 请求", e);
        }
    }

    private Answer parseAnswer(JSONObject payload, int fallbackQuestionNo) throws DeepSeekException {
        String status = payload.optString("status", "").trim().toLowerCase(Locale.ROOT);
        if ("error".equals(status)) {
            String message = payload.optString("message",
                    payload.optString("answer_text", "DeepSeek 返回错误"));
            throw new DeepSeekException(ErrorKind.PROTOCOL, "DeepSeek 返回错误：" + sanitize(message, 180));
        }
        if ("unknown".equals(status)) {
            String message = payload.optString("message", "无法从截图确定答案");
            throw new DeepSeekException(ErrorKind.PROTOCOL, sanitize(message, 180));
        }
        String answer = normalizeAnswer(valueToString(payload.opt("answer")));
        if (answer.isEmpty()) {
            throw new DeepSeekException(ErrorKind.PROTOCOL, "DeepSeek 返回中没有 answer 字段");
        }
        if (answer.length() > 200) {
            throw new DeepSeekException(ErrorKind.PROTOCOL, "answer 字段过长，拒绝显示");
        }
        int questionNo = payload.optInt("question_no", fallbackQuestionNo);
        if (questionNo < 1 || questionNo > 999) questionNo = fallbackQuestionNo;
        String answerText = payload.optString("answer_text", "");
        if (answerText == null) answerText = "";
        double confidence = payload.optDouble("confidence", 0.0);
        if (Double.isNaN(confidence) || Double.isInfinite(confidence)) confidence = 0.0;
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        return new Answer(questionNo, answer, answerText, confidence,
                payload.optBoolean("all_known", false));
    }

    private String extractContent(String body) throws DeepSeekException {
        try {
            JSONObject response = new JSONObject(body);
            JSONArray choices = response.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new DeepSeekException(ErrorKind.PROTOCOL, "DeepSeek 返回缺少 choices");
            }
            JSONObject choice = choices.optJSONObject(0);
            if (choice == null) {
                throw new DeepSeekException(ErrorKind.PROTOCOL, "DeepSeek 返回 choices 格式异常");
            }
            JSONObject message = choice.optJSONObject("message");
            if (message == null) {
                throw new DeepSeekException(ErrorKind.PROTOCOL, "DeepSeek 返回缺少 message");
            }
            Object content = message.opt("content");
            if (content instanceof JSONArray) {
                StringBuilder text = new StringBuilder();
                JSONArray parts = (JSONArray) content;
                for (int i = 0; i < parts.length(); i++) {
                    JSONObject part = parts.optJSONObject(i);
                    if (part != null && "text".equals(part.optString("type"))) {
                        if (text.length() > 0) text.append('\n');
                        text.append(part.optString("text", ""));
                    }
                }
                return text.toString();
            }
            return content == null || content == JSONObject.NULL ? "" : String.valueOf(content);
        } catch (DeepSeekException e) {
            throw e;
        } catch (Exception e) {
            throw new DeepSeekException(ErrorKind.PROTOCOL, "无法解析 DeepSeek 响应", e);
        }
    }

    private HttpResult request(String method, String endpoint, String jsonBody) throws DeepSeekException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            currentConnection = connection;
            connection.setRequestMethod(method);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(65000);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "ScreenCoach/1.0 DeepSeek");
            if (jsonBody != null) {
                byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = stream == null ? "" : readLimited(stream);
            return new HttpResult(code, body);
        } catch (java.net.SocketTimeoutException e) {
            throw new DeepSeekException(ErrorKind.NETWORK, "DeepSeek 请求超时", e);
        } catch (java.net.SocketException e) {
            throw new DeepSeekException(ErrorKind.CANCELLED, "请求已取消", e);
        } catch (IOException e) {
            String message = e.getMessage() == null ? "网络错误" : e.getMessage();
            if (message.toLowerCase(Locale.ROOT).contains("closed")
                    || message.toLowerCase(Locale.ROOT).contains("reset")) {
                throw new DeepSeekException(ErrorKind.CANCELLED, "请求已取消", e);
            }
            throw new DeepSeekException(ErrorKind.NETWORK,
                    "无法连接 DeepSeek：" + sanitize(message, 120), e);
        } finally {
            if (currentConnection == connection) currentConnection = null;
            if (connection != null) {
                try { connection.disconnect(); } catch (Throwable ignored) { }
            }
        }
    }

    private static String readLimited(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) throw new IOException("响应过大");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String errorMessage(String body) {
        try {
            JSONObject error = new JSONObject(body).optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "");
                if (!message.isEmpty()) return message;
            }
        } catch (Exception ignored) { }
        return body == null ? "" : body.replaceAll("\\s+", " ").trim();
    }

    private static String valueToString(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                String piece = valueToString(array.opt(i));
                if (!piece.isEmpty()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(piece);
                }
            }
            return sb.toString();
        }
        return String.valueOf(value).trim();
    }

    private static String normalizeAnswer(String answer) {
        String value = answer == null ? "" : answer.trim();
        if (value.isEmpty()) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^(?:答案\\s*[:：]?\\s*)?([A-Ha-h])(?:\\s*[.。、:：)）]\\s*.*)?$",
                        java.util.regex.Pattern.DOTALL)
                .matcher(value);
        if (matcher.matches()) return matcher.group(1).toUpperCase(Locale.ROOT);
        return value;
    }

    private static JSONObject extractJsonObject(String text) {
        if (text == null) return null;
        String value = text.trim();
        if (value.startsWith("```")) {
            int firstLine = value.indexOf('\n');
            if (firstLine >= 0) value = value.substring(firstLine + 1);
            if (value.endsWith("```")) value = value.substring(0, value.length() - 3);
            value = value.trim();
        }
        try {
            return new JSONObject(value);
        } catch (Exception ignored) { }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                return new JSONObject(value.substring(start, end + 1));
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static String sanitize(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }

    private static final class HttpResult {
        final int code;
        final String body;
        HttpResult(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }
}
