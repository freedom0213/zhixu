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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class KnowledgeService {
    private final AiProperties properties;
    private final Map<String, String> documents = new ConcurrentHashMap<>();
    /** docId -> 归属用户 ID（由 documents/{userId}/ 目录结构持久化）。 */
    private final Map<String, Long> docOwner = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> sessions = new ConcurrentHashMap<>();
    private final AtomicLong recordSequence = new AtomicLong();
    private final ChatLanguageModel model;
    private final StreamingChatLanguageModel streamingModel;
    private final EmbeddingModel embeddingModel;
    private final ChunkIndex chunkIndex;
    private final PgVectorStore vectorStore;
    private final boolean embeddingEnabled;

    public KnowledgeService(AiProperties properties) {
        this.properties = properties;
        Path dir = Paths.get(properties.getDataDir(), "documents");
        Path indexPath = Paths.get(properties.getDataDir(), "index", "chunks.json");
        try {
            Files.createDirectories(dir);
            // 目录结构：documents/{userId}/{文件名}；历史单层目录视为 userId=null
            try (var userDirs = Files.list(dir)) {
                userDirs.filter(Files::isDirectory).forEach(ud -> {
                    Long uid = parseLongOrNull(ud.getFileName().toString());
                    try (var paths = Files.list(ud)) {
                        paths.filter(Files::isRegularFile).forEach(p -> loadDocument(p, uid));
                    } catch (IOException ignored) { }
                });
                try (var paths = Files.list(dir)) {
                    paths.filter(Files::isRegularFile).forEach(p -> loadDocument(p, null));
                }
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
        String embKey = (properties.getEmbeddingApiKey() == null || properties.getEmbeddingApiKey().isBlank())
                ? properties.getApiKey()
                : properties.getEmbeddingApiKey();
        this.embeddingModel = this.embeddingEnabled
                ? OpenAiEmbeddingModel.builder()
                    .apiKey(embKey)
                    .baseUrl(embUrl)
                    .modelName(properties.getEmbeddingModel())
                    .build()
                : null;
        this.vectorStore = properties.isVectorStoreEnabled() && this.embeddingEnabled
                ? new PgVectorStore(properties)
                : null;
    }

    private void loadDocument(Path p, Long userId) {
        try {
            String name = p.getFileName().toString();
            documents.put(name, Files.readString(p));
            // ConcurrentHashMap 不允许 null value，历史无主文件用 -1L 哨兵
            docOwner.put(name, userId == null ? -1L : userId);
        } catch (IOException ignored) { }
    }

    private static Long parseLongOrNull(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    public Map<String, Object> upload(Long userId, MultipartFile file) throws IOException {
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("document.txt");
        if (!(name.toLowerCase(Locale.ROOT).endsWith(".md") || name.toLowerCase(Locale.ROOT).endsWith(".markdown") || name.toLowerCase(Locale.ROOT).endsWith(".txt")))
            throw new IllegalArgumentException("仅支持 Markdown 或 TXT 文件");
        String safe = UUID.randomUUID() + "-" + Paths.get(name).getFileName();
        // 未登录禁止上传知识库文件
        if (userId == null) throw new IllegalArgumentException("请先登录后再上传知识库文件");
        // 同一用户下同名文件只能存在一份
        if (list(userId).stream().anyMatch(d -> name.equals(d.get("name")))) {
            throw new IllegalArgumentException("知识库中已存在同名文件「" + name + "」，请先删除旧文件或重命名后再上传");
        }
        Path path = Paths.get(properties.getDataDir(), "documents", String.valueOf(userId), safe);
        Files.createDirectories(path.getParent());
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        Files.writeString(path, text);
        documents.put(safe, text);
        docOwner.put(safe, userId);

        // 结构化切块 + embedding
        List<KnowledgeChunk> chunks = buildChunks(safe, name, text, userId);
        if (embeddingEnabled) embedChunks(chunks);
        chunkIndex.addAll(safe, name, chunks);
        if (vectorStore != null) vectorStore.addAll(chunks);

        return Map.of("id", safe, "name", name, "size", text.length(), "chunkCount", chunks.size());
    }

    public int chunkTotal(Long userId) { return (int) chunkIndex.all().stream().filter(c -> owns(c, userId)).count(); }

    private static boolean owns(KnowledgeChunk c, Long userId) {
        return userId != null && Objects.equals(c.getUserId(), userId);
    }

    public List<Map<String, Object>> list(Long userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (userId == null) return result;
        documents.forEach((name, text) -> {
            if (!Objects.equals(docOwner.get(name), userId)) return;
            int separator = name.length() > 37 && name.charAt(36) == '-' ? 36 : name.indexOf('-');
            String displayName = separator >= 0 && separator + 1 < name.length() ? name.substring(separator + 1) : name;
            int chunkCount = (int) chunkIndex.all().stream().filter(c -> name.equals(c.getDocId())).count();
            result.add(Map.of("id", name, "name", displayName, "size", text.length(), "chunkCount", chunkCount));
        });
        return result;
    }

    public String content(Long userId, String id) throws IOException {
        if (userId == null || !Objects.equals(docOwner.get(id), userId)) throw new NoSuchFileException(id);
        String value = documents.get(id);
        if (value == null) throw new NoSuchFileException(id);
        return value;
    }

    public void delete(Long userId, String id) throws IOException {
        if (userId == null || !Objects.equals(docOwner.get(id), userId)) throw new NoSuchFileException(id);
        documents.remove(id);
        docOwner.remove(id);
        Files.deleteIfExists(Paths.get(properties.getDataDir(), "documents", String.valueOf(userId), id));
        chunkIndex.removeByDoc(id);
        if (vectorStore != null) vectorStore.removeByDoc(id);
    }

    /** 校验会话归属：不存在或非本人会话返回 null。 */
    private Map<String, Object> ownSession(Long userId, String id) {
        if (userId == null || id == null || id.isBlank()) return null;
        Map<String, Object> session = sessions.get(id);
        if (session == null) return null;
        return Objects.equals(session.get("userId"), userId) ? session : null;
    }

    public List<Map<String, Object>> sessions(Long userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (userId == null) return result;
        sessions.values().forEach(s -> {
            if (Objects.equals(s.get("userId"), userId)) result.add(s);
        });
        return result;
    }

    public Map<String, Object> createSession(Long userId, String name, String tag) {
        if (userId == null) throw new IllegalArgumentException("请先登录后再创建会话");
        Map<String, Object> session = new LinkedHashMap<>();
        String id = UUID.randomUUID().toString();
        session.put("id", id); session.put("sessionId", id);
        session.put("userId", userId);
        session.put("name", name); session.put("tag", tag); sessions.put(id, session);
        return session;
    }

    public void deleteSession(Long userId, String id) {
        if (ownSession(userId, id) == null) return;
        sessions.remove(id);
    }

    public List<Map<String, Object>> records(Long userId, String id) {
        Map<String, Object> session = ownSession(userId, id);
        if (session == null) return Collections.emptyList();
        @SuppressWarnings("unchecked") List<Map<String, Object>> records = (List<Map<String, Object>>) session.get("records");
        if (records == null) return Collections.emptyList();
        synchronized (records) { return new ArrayList<>(records); }
    }

    public Map<String, Object> updateSession(Long userId, String id, String name, String tag) {
        Map<String, Object> session = ownSession(userId, id);
        if (session != null) { session.put("name", name); session.put("tag", tag); }
        return session;
    }

    public String chat(Long userId, String question) { return chat(null, userId, question); }

    public String chat(String sessionId, Long userId, String question) {
        String query = question == null ? "" : question.trim();
        List<KnowledgeChunk> ctx = retrieve(userId, query);
        String answer = answerWithContext(ctx, query);
        appendRecords(userId, sessionId, query, answer);
        return answer;
    }

    /** Streams provider tokens while retaining the local fallback for offline demos. */
    public void streamChat(String sessionId, Long userId, String question, Consumer<String> onToken,
                           Consumer<String> onComplete, Consumer<Throwable> onError) {
        String query = question == null ? "" : question.trim();
        List<KnowledgeChunk> ctx = retrieve(userId, query);
        if (streamingModel == null) {
            String answer = localAnswer(ctx);
            CompletableFuture.runAsync(() -> {
                try {
                    for (int start = 0; start < answer.length(); start += 12) {
                        onToken.accept(answer.substring(start, Math.min(start + 12, answer.length())));
                        Thread.sleep(18L);
                    }
                    appendRecords(userId, sessionId, query, answer);
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
                    appendRecords(userId, sessionId, query, complete);
                    onComplete.accept(complete);
                }

                @Override public void onError(Throwable error) { onError.accept(error); }
            });
        } catch (Throwable error) {
            onError.accept(error);
        }
    }

    /** 检索：embedding 启用时走向量 topK，未启用或失败时走关键词兜底。仅检索当前用户自己的 chunk。 */
    private List<KnowledgeChunk> retrieve(Long userId, String query) {
        if (userId == null) return Collections.emptyList();
        if (query.isEmpty() || chunkIndex.isEmpty()) return Collections.emptyList();
        List<KnowledgeChunk> mine = new ArrayList<>();
        for (KnowledgeChunk c : chunkIndex.all()) if (owns(c, userId)) mine.add(c);
        if (mine.isEmpty()) return Collections.emptyList();
        if (embeddingEnabled) {
            try {
                float[] q = embed(query);
                List<KnowledgeChunk> out = new ArrayList<>();
                // pgvector 可用时由向量库做 ANN 检索（向量召回后再按 userId 过滤）
                if (vectorStore != null && vectorStore.isAvailable()) {
                    Map<String, KnowledgeChunk> byId = new HashMap<>();
                    for (KnowledgeChunk c : mine) byId.put(c.getChunkId(), c);
                    for (PgVectorStore.ScoredId s : vectorStore.search(q, properties.getTopK())) {
                        KnowledgeChunk c = byId.get(s.chunkId);
                        if (c != null) out.add(c);
                    }
                } else {
                    // 本地 cosine 内存检索（pgvector 不可用时的降级）
                    int k = Math.min(properties.getTopK(), mine.size());
                    if (k <= 0) return Collections.emptyList();
                    PriorityQueue<Scored> heap = new PriorityQueue<>(Comparator.comparingDouble(Scored::score));
                    for (KnowledgeChunk c : mine) {
                        if (c.getEmbedding() == null) continue;
                        double sc = cosine(q, c.getEmbedding());
                        if (heap.size() < k) heap.offer(new Scored(c, sc));
                        else if (sc > heap.peek().score) { heap.poll(); heap.offer(new Scored(c, sc)); }
                    }
                    while (!heap.isEmpty()) out.add(heap.poll().chunk);
                    Collections.reverse(out);
                }
                // 关键词兜底追加（去重，仅本用户）
                Set<String> seen = new HashSet<>();
                for (KnowledgeChunk c : out) seen.add(c.getChunkId());
                for (KnowledgeChunk c : mine) {
                    if (keywordHit(c, query) && seen.add(c.getChunkId())) out.add(c);
                }
                return out;
            } catch (Throwable ignored) {
                // embedding 失败 → 关键词兜底
            }
        }
        List<KnowledgeChunk> out = new ArrayList<>();
        for (KnowledgeChunk c : mine) {
            if (keywordHit(c, query)) out.add(c);
            if (out.size() >= properties.getTopK() + properties.getKeywordFallbackK()) break;
        }
        return out;
    }

    /** 与 ChunkIndex.keywordSearch 相同的关键词命中逻辑，但仅作用于给定 chunk。 */
    private boolean keywordHit(KnowledgeChunk c, String query) {
        if (c == null || c.getKeywords() == null || query == null || query.isEmpty()) return false;
        String lower = query.toLowerCase(Locale.ROOT);
        List<String> kws = c.getKeywords();
        for (int i = 0; i + 1 < lower.length(); i++) {
            char a = lower.charAt(i), b = lower.charAt(i + 1);
            if (a > 127 && b > 127 && kws.contains(lower.substring(i, i + 2))) return true;
        }
        Matcher tokenM = Pattern.compile("[a-z0-9]{2,}").matcher(lower);
        while (tokenM.find()) if (kws.contains(tokenM.group())) return true;
        return false;
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

    private List<KnowledgeChunk> buildChunks(String docId, String docName, String text, Long userId) {
        List<MarkdownChunker.RawChunk> raws = MarkdownChunker.chunk(
                text, docName, properties.getChunkSize(), properties.getChunkOverlap());
        List<KnowledgeChunk> out = new ArrayList<>(raws.size());
        for (MarkdownChunker.RawChunk r : raws) {
            String chunkText = r.text == null ? "" : r.text;
            List<String> kws = MarkdownChunker.keywords(chunkText);
            out.add(new KnowledgeChunk(
                    UUID.randomUUID().toString(),
                    docId, userId, docName,
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

    private void appendRecords(Long userId, String sessionId, String question, String answer) {
        Map<String, Object> session = ownSession(userId, sessionId);
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
