package com.zhixu.ai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Service
public class KnowledgeService {
    private final AiProperties properties;
    private final Map<String, String> documents = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> sessions = new ConcurrentHashMap<>();
    private final AtomicLong recordSequence = new AtomicLong();
    private final ChatLanguageModel model;
    private final StreamingChatLanguageModel streamingModel;

    public KnowledgeService(AiProperties properties) {
        this.properties = properties;
        Path dir = Paths.get(properties.getDataDir(), "documents");
        try {
            Files.createDirectories(dir);
            try (var paths = Files.list(dir)) {
                paths.filter(Files::isRegularFile).forEach(p -> {
                    try { documents.put(p.getFileName().toString(), Files.readString(p)); } catch (IOException ignored) { }
                });
            }
        } catch (IOException ignored) { }
        boolean configured = properties.isEnabled() && properties.getApiKey() != null
                && !properties.getApiKey().isBlank() && !"your key".equalsIgnoreCase(properties.getApiKey());
        this.model = configured
                ? OpenAiChatModel.builder().apiKey(properties.getApiKey()).baseUrl(properties.getBaseUrl()).modelName(properties.getModel()).build()
                : null;
        this.streamingModel = configured
                ? OpenAiStreamingChatModel.builder().apiKey(properties.getApiKey()).baseUrl(properties.getBaseUrl()).modelName(properties.getModel()).build()
                : null;
    }

    public Map<String, Object> upload(MultipartFile file) throws IOException {
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("document.txt");
        if (!(name.toLowerCase(Locale.ROOT).endsWith(".md") || name.toLowerCase(Locale.ROOT).endsWith(".markdown") || name.toLowerCase(Locale.ROOT).endsWith(".txt")))
            throw new IllegalArgumentException("仅支持 Markdown 或 TXT 文件");
        String safe = UUID.randomUUID() + "-" + Paths.get(name).getFileName();
        Path path = Paths.get(properties.getDataDir(), "documents", safe);
        Files.createDirectories(path.getParent());
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        Files.writeString(path, text);
        documents.put(safe, text);
        return Map.of("id", safe, "name", name, "size", text.length());
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        documents.forEach((name, text) -> {
            int separator = name.length() > 37 && name.charAt(36) == '-' ? 36 : name.indexOf('-');
            String displayName = separator >= 0 && separator + 1 < name.length() ? name.substring(separator + 1) : name;
            result.add(Map.of("id", name, "name", displayName, "size", text.length()));
        });
        return result;
    }

    public String content(String id) throws IOException {
        String value = documents.get(id);
        if (value == null) throw new NoSuchFileException(id);
        return value;
    }

    public void delete(String id) throws IOException {
        documents.remove(id);
        Files.deleteIfExists(Paths.get(properties.getDataDir(), "documents", id));
    }

    public List<Map<String, Object>> sessions() { return new ArrayList<>(sessions.values()); }

    public Map<String, Object> createSession(String name, String tag) {
        Map<String, Object> session = new LinkedHashMap<>();
        String id = UUID.randomUUID().toString();
        session.put("id", id); session.put("sessionId", id);
        session.put("name", name); session.put("tag", tag); sessions.put(id, session);
        return session;
    }

    public void deleteSession(String id) { sessions.remove(id); }

    public List<Map<String, Object>> records(String id) {
        if (id == null || id.isBlank()) return Collections.emptyList();
        Map<String, Object> session = sessions.get(id);
        if (session == null) return Collections.emptyList();
        @SuppressWarnings("unchecked") List<Map<String, Object>> records = (List<Map<String, Object>>) session.get("records");
        if (records == null) return Collections.emptyList();
        synchronized (records) { return new ArrayList<>(records); }
    }

    public Map<String, Object> updateSession(String id, String name, String tag) {
        Map<String, Object> session = sessions.get(id);
        if (session != null) { session.put("name", name); session.put("tag", tag); }
        return session;
    }

    public String chat(String question) { return chat(null, question); }

    public String chat(String sessionId, String question) {
        String query = question == null ? "" : question.trim();
        return answerWithContext(sessionId, query, findContext(query));
    }

    private String answerWithContext(String sessionId, String question, String context) {
        String answer = model == null ? localAnswer(context) : model.generate(buildPrompt(question, context));
        appendRecords(sessionId, question, answer);
        return answer;
    }

    /** Streams provider tokens while retaining the local fallback for offline demos. */
    public void streamChat(String sessionId, String question, Consumer<String> onToken,
                           Consumer<String> onComplete, Consumer<Throwable> onError) {
        String query = question == null ? "" : question.trim();
        String context = findContext(query);
        if (streamingModel == null) {
            String answer = localAnswer(context);
            // Keep the offline demo visually streaming as well, without requiring a model key.
            CompletableFuture.runAsync(() -> {
                try {
                    for (int start = 0; start < answer.length(); start += 12) {
                        onToken.accept(answer.substring(start, Math.min(start + 12, answer.length())));
                        Thread.sleep(18L);
                    }
                    appendRecords(sessionId, query, answer);
                    onComplete.accept(answer);
                } catch (Throwable error) {
                    onError.accept(error);
                }
            });
            return;
        }
        StringBuilder answer = new StringBuilder();
        try {
            streamingModel.generate(buildPrompt(query, context), new StreamingResponseHandler<AiMessage>() {
                @Override public void onNext(String token) {
                    if (token == null || token.isEmpty()) return;
                    answer.append(token);
                    onToken.accept(token);
                }

                @Override public void onComplete(dev.langchain4j.model.output.Response<AiMessage> response) {
                    String complete = answer.toString();
                    appendRecords(sessionId, query, complete);
                    onComplete.accept(complete);
                }

                @Override public void onError(Throwable error) { onError.accept(error); }
            });
        } catch (Throwable error) {
            onError.accept(error);
        }
    }

    private String findContext(String query) {
        if (query.contains("集合")) {
            String section = collectionSection();
            if (!section.isBlank()) return section;
        }
        String context = documents.entrySet().stream()
                .filter(e -> matchesDocument(e.getKey(), query))
                .flatMap(e -> Arrays.stream(e.getValue().split("\\n\\s*\\n")))
                .filter(s -> containsKeyword(s, query)).limit(12)
                .reduce((a, b) -> a + "\n\n" + b).orElse("");
        if (context.isEmpty() && (query.toLowerCase(Locale.ROOT).contains("java") || query.contains("类") || query.contains("对象"))) {
            context = documents.entrySet().stream().filter(e -> e.getKey().toLowerCase(Locale.ROOT).contains("java"))
                    .map(Map.Entry::getValue).flatMap(t -> Arrays.stream(t.split("\\n\\s*\\n")))
                    .filter(s -> query.contains("集合") ? s.contains("集合") || s.contains("List") || s.contains("Map") || s.contains("ArrayList") : true)
                    .limit(12).reduce((a, b) -> a + "\n\n" + b).orElse("");
        }
        return context;
    }

    private String buildPrompt(String question, String context) {
        return "你是知序学堂课程助手。请优先依据参考资料回答；资料涉及相关概念时，可用简洁的基础知识补充解释，不要编造与问题无关的内容。资料确实没有涉及时，再明确说明。\n参考资料：\n" + context + "\n问题：" + question;
    }

    private String localAnswer(String context) {
        return context.isEmpty() ? "知识库中没有找到与该问题相关的内容。" : "已检索到相关知识片段：\n\n" + context;
    }

    private void appendRecords(String sessionId, String question, String answer) {
        if (sessionId == null) return;
        Map<String, Object> session = sessions.get(sessionId);
        if (session == null) return;
        @SuppressWarnings("unchecked") List<Map<String, Object>> records = (List<Map<String, Object>>) session.computeIfAbsent("records", k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (records) {
            records.add(record("USER", question));
            records.add(record("AI", answer));
        }
    }

    private String collectionSection() {
        for (String text : documents.values()) {
            int start = text.indexOf("集合框架");
            if (start < 0) continue;
            int sectionStart = text.lastIndexOf("##", start);
            int next = text.indexOf("\n## ", start + 2);
            return text.substring(Math.max(0, sectionStart), next > 0 ? next : text.length()).trim();
        }
        return "";
    }

    private Map<String, Object> record(String type, String text) {
        Map<String, Object> content = new LinkedHashMap<>(); content.put("type", type);
        if ("USER".equals(type)) content.put("contents", List.of(Map.of("text", text))); else content.put("text", text);
        return Map.of("content", new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(content).toString(), "segmentIndex", recordSequence.incrementAndGet());
    }

    private boolean matchesDocument(String name, String query) {
        String q = query.toLowerCase(Locale.ROOT);
        if (q.contains("python") || q.contains("蟒蛇")) return name.toLowerCase(Locale.ROOT).contains("python");
        if (q.contains("java") || q.contains("类") || q.contains("对象") || q.contains("集合")) return name.toLowerCase(Locale.ROOT).contains("java");
        return true;
    }

    private boolean containsKeyword(String text, String question) {
        String normalizedText = text.toLowerCase(Locale.ROOT);
        String normalizedQuestion = question.toLowerCase(Locale.ROOT).replaceAll("([a-z0-9]+)(?=[\\u4e00-\\u9fff])", "$1 ")
                .replaceAll("(?<=[\\u4e00-\\u9fff])([a-z0-9]+)", " $1");
        for (String token : normalizedQuestion.split("\\s+|[，。！？、]")) {
            if (token.length() > 1 && normalizedText.contains(token)) return true;
            if (token.length() >= 2 && token.chars().allMatch(c -> c > 127)) {
                for (int i = 0; i + 1 < token.length(); i++) if (normalizedText.contains(token.substring(i, i + 2))) return true;
            }
        }
        return false;
    }
}
