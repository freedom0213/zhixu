package com.zhixu.ai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    private final EmbeddingModel embeddingModel;
    private final ChunkIndex chunkIndex;
    private final boolean embeddingEnabled;

    public KnowledgeService(AiProperties properties) {
        this.properties = properties;
        Path dir = Paths.get(properties.getDataDir(), "documents");
        Path indexPath = Paths.get(properties.getDataDir(), "index", "chunks.json");
        try {
            Files.createDirectories(dir);
            try (var paths = Files.list(dir)) {
                paths.filter(Files::isRegularFile).forEach(p -> {
                    try { documents.put(p.getFileName().toString(), Files.readString(p)); } catch (IOException ignored) { }
                });
            }
        } catch (IOException ignored) { }
        this.chunkIndex = new ChunkIndex(indexPath);

        boolean configured = properties.isEnabled() && properties.getApiKey() != null
                && !properties.getApiKey().isBlank() && !"your key".equalsIgnoreCase(properties.getApiKey());
        this.model = configured
                ? OpenAiChatModel.builder().apiKey(properties.getApiKey()).baseUrl(properties.getBaseUrl()).modelName(properties.getModel()).build()
                : null;
        this.streamingModel = configured
                ? OpenAiStreamingChatModel.builder().apiKey(properties.getApiKey()).baseUrl(properties.getBaseUrl()).modelName(properties.getModel()).build()
                : null;
        String embUrl = (properties.getEmbeddingBaseUrl() == null || properties.getEmbeddingBaseUrl().isBlank())
                ? properties.getBaseUrl()
                : properties.getEmbeddingBaseUrl();
        this.embeddingEnabled = configured && properties.getEmbeddingModel() != null && !properties.getEmbeddingModel().isBlank();
        this.embeddingModel = this.embeddingEnabled
                ? OpenAiEmbeddingModel.builder()
                    .apiKey(properties.getApiKey())
                    .baseUrl(embUrl)
                    .modelName(properties.getEmbeddingModel())
                    .build()
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

        // 结构化切块 + embedding
        List<KnowledgeChunk> chunks = buildChunks(safe, name, text);
        if (embeddingEnabled) embedChunks(chunks);
        chunkIndex.addAll(safe, name, chunks);

        return Map.of("id", safe, "name", name, "size", text.length(), "chunkCount", chunks.size());
    }

    public int chunkTotal() { return chunkIndex.size(); }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        documents.forEach((name, text) -> {
            int separator = name.length() > 37 && name.charAt(36) == '-' ? 36 : name.indexOf('-');
            String displayName = separator >= 0 && separator + 1 < name.length() ? name.substring(separator + 1) : name;
            int chunkCount = (int) chunkIndex.all().stream().filter(c -> name.equals(c.getDocId())).count();
            result.add(Map.of("id", name, "name", displayName, "size", text.length(), "chunkCount", chunkCount));
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
        chunkIndex.removeByDoc(id);
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
        List<KnowledgeChunk> ctx = retrieve(query);
        String answer = answerWithContext(ctx, query);
        appendRecords(sessionId, query, answer);
        return answer;
    }

    /** Streams provider tokens while retaining the local fallback for offline demos. */
    public void streamChat(String sessionId, String question, Consumer<String> onToken,
                           Consumer<String> onComplete, Consumer<Throwable> onError) {
        String query = question == null ? "" : question.trim();
        List<KnowledgeChunk> ctx = retrieve(query);
        if (streamingModel == null) {
            String answer = localAnswer(ctx);
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
            streamingModel.generate(buildPrompt(query, ctx), new StreamingResponseHandler<AiMessage>() {
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

    /** 检索：embedding 启用时走向量 topK，未启用或失败时走关键词兜底。 */
    private List<KnowledgeChunk> retrieve(String query) {
        if (query.isEmpty() || chunkIndex.isEmpty()) return Collections.emptyList();
        if (embeddingEnabled) {
            try {
                float[] q = embed(query);
                List<KnowledgeChunk> all = chunkIndex.all();
                int k = Math.min(properties.getTopK(), all.size());
                if (k <= 0) return Collections.emptyList();
                PriorityQueue<Scored> heap = new PriorityQueue<>(Comparator.comparingDouble(Scored::score));
                for (KnowledgeChunk c : all) {
                    if (c.getEmbedding() == null) continue;
                    double s = cosine(q, c.getEmbedding());
                    if (heap.size() < k) heap.offer(new Scored(c, s));
                    else if (s > heap.peek().score) { heap.poll(); heap.offer(new Scored(c, s)); }
                }
                List<KnowledgeChunk> out = new ArrayList<>();
                while (!heap.isEmpty()) out.add(heap.poll().chunk);
                Collections.reverse(out);
                // 关键词兜底追加（去重）
                Set<String> seen = new HashSet<>();
                for (KnowledgeChunk c : out) seen.add(c.getChunkId());
                for (KnowledgeChunk c : chunkIndex.keywordSearch(query, properties.getKeywordFallbackK())) {
                    if (seen.add(c.getChunkId())) out.add(c);
                }
                return out;
            } catch (Throwable ignored) {
                // embedding 失败 → 关键词兜底
            }
        }
        return chunkIndex.keywordSearch(query, properties.getTopK() + properties.getKeywordFallbackK());
    }

    private float[] embed(String text) {
        if (embeddingModel == null) return new float[0];
        Embedding e = embeddingModel.embed(text).content();
        return e.vector();
    }

    private void embedChunks(List<KnowledgeChunk> chunks) {
        if (embeddingModel == null || chunks == null || chunks.isEmpty()) return;
        try {
            for (KnowledgeChunk c : chunks) {
                if (c.getText() == null || c.getText().isEmpty()) continue;
                c.setEmbedding(embed(c.getText()));
            }
        } catch (Throwable ignored) {
            // 单个失败不阻断，关键词兜底仍可工作
        }
    }

    private List<KnowledgeChunk> buildChunks(String docId, String docName, String text) {
        List<MarkdownChunker.RawChunk> raws = MarkdownChunker.chunk(
                text, docName, properties.getChunkSize(), properties.getChunkOverlap());
        List<KnowledgeChunk> out = new ArrayList<>(raws.size());
        for (MarkdownChunker.RawChunk r : raws) {
            String chunkText = r.text == null ? "" : r.text;
            List<String> kws = MarkdownChunker.keywords(chunkText);
            out.add(new KnowledgeChunk(
                    UUID.randomUUID().toString(),
                    docId, docName,
                    r.sectionPath,
                    r.heading,
                    0, chunkText.length(),
                    chunkText, kws,
                    chunkText.length() / 2,
                    null
            ));
        }
        return out;
    }

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) return 0d;
        double dot = 0d, na = 0d, nb = 0d;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        if (na == 0d || nb == 0d) return 0d;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private String answerWithContext(List<KnowledgeChunk> ctx, String question) {
        if (model == null) return localAnswer(ctx);
        return model.generate(buildPrompt(question, ctx));
    }

    private String buildPrompt(String question, List<KnowledgeChunk> ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是知序学堂课程助手。请优先依据参考资料回答；资料涉及相关概念时，");
        sb.append("可用简洁的基础知识补充解释，不要编造与问题无关的内容。资料确实没有涉及时，再明确说明。\n参考资料：\n");
        if (ctx == null || ctx.isEmpty()) {
            sb.append("（无）\n");
        } else {
            for (KnowledgeChunk c : ctx) {
                String ref = (c.getSectionPath() == null || c.getSectionPath().isEmpty())
                        ? c.getDocName()
                        : String.join(" > ", c.getSectionPath());
                sb.append("[").append(ref).append("] ").append(c.getText()).append("\n\n");
            }
        }
        sb.append("问题：").append(question);
        return sb.toString();
    }

    private String localAnswer(List<KnowledgeChunk> ctx) {
        if (ctx == null || ctx.isEmpty()) return "知识库中没有找到与该问题相关的内容。";
        StringBuilder sb = new StringBuilder("已检索到相关知识片段：\n\n");
        for (KnowledgeChunk c : ctx) {
            String ref = (c.getSectionPath() == null || c.getSectionPath().isEmpty())
                    ? c.getDocName()
                    : String.join(" > ", c.getSectionPath());
            sb.append("[").append(ref).append("] ").append(c.getText()).append("\n\n");
        }
        return sb.toString();
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

    private Map<String, Object> record(String type, String text) {
        Map<String, Object> content = new LinkedHashMap<>(); content.put("type", type);
        if ("USER".equals(type)) content.put("contents", List.of(Map.of("text", text))); else content.put("text", text);
        return Map.of("content", new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(content).toString(), "segmentIndex", recordSequence.incrementAndGet());
    }

    private static final class Scored {
        final KnowledgeChunk chunk;
        final double score;
        Scored(KnowledgeChunk c, double s) { this.chunk = c; this.score = s; }
        double score() { return score; }
    }
}
