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
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识库 + 聊天核心服务。
 *
 * 知识库按「归属」划分：
 *   - USER   owner：私人知识库（用户上传，userId=用户ID）
 *   - COURSE owner：课程知识库（老师/管理端上传，userId=课程ID）
 * 会话按「助手类型」划分：GLOBAL / COURSE / PRIVATE（见 ChatSessionStore）。
 * 检索与提示词都随 owner / assistantType 切换。
 */
@Service
public class KnowledgeService {
    private final AiProperties properties;
    /** safe 文件名 -> 文档内容。 */
    private final Map<String, String> documents = new ConcurrentHashMap<>();
    /** safe 文件名 -> 归属 key（"user:{id}" / "course:{id}" / "user:-1" 历史无主）。 */
    private final Map<String, String> docOwner = new ConcurrentHashMap<>();
    private final ChatSessionStore sessionStore;
    private final ChatLanguageModel model;
    private final StreamingChatLanguageModel streamingModel;
    private final EmbeddingModel embeddingModel;
    private final ChunkIndex chunkIndex;
    private final PgVectorStore vectorStore;
    private final RerankClient rerankClient;
    private final boolean embeddingEnabled;

    public KnowledgeService(AiProperties properties) {
        this.properties = properties;
        this.sessionStore = new ChatSessionStore(properties);
        Path dir = Paths.get(properties.getDataDir(), "documents");
        Path indexPath = Paths.get(properties.getDataDir(), "index", "chunks.json");
        try {
            Files.createDirectories(dir);
            // 目录结构（新）：documents/user/{userId}/、documents/course/{courseId}/、documents/platform/0/
            // 兼容（旧）：documents/{userId}/（数字目录视为用户）、documents/{文件}（无主）
            try (var topDirs = Files.list(dir)) {
                topDirs.filter(Files::isDirectory).forEach(top -> {
                    String topName = top.getFileName().toString();
                    if (topName.equals("user") || topName.equals("course") || topName.equals("platform")) {
                        String ownerType = topName;
                        try (var ownerDirs = Files.list(top)) {
                            ownerDirs.filter(Files::isDirectory).forEach(od -> {
                                Long oid = parseLongOrNull(od.getFileName().toString());
                                if (oid == null) return;
                                try (var paths = Files.list(od)) {
                                    paths.filter(Files::isRegularFile).forEach(p -> loadDocument(p, ownerKey(ownerType, oid)));
                                } catch (IOException ignored) { }
                            });
                        } catch (IOException ignored) { }
                    } else {
                        Long uid = parseLongOrNull(topName);
                        if (uid != null) {
                            try (var paths = Files.list(top)) {
                                paths.filter(Files::isRegularFile).forEach(p -> loadDocument(p, ownerKey("user", uid)));
                            } catch (IOException ignored) { }
                        }
                    }
                });
                try (var paths = Files.list(dir)) {
                    paths.filter(Files::isRegularFile).forEach(p -> loadDocument(p, "user:-1"));
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
        this.rerankClient = new RerankClient(properties);
    }

    public ChatSessionStore sessionStore() { return sessionStore; }

    // ==================== 归属（owner） ====================

    public static String ownerKey(String type, Long id) {
        return type + ":" + (id == null ? -1L : id);
    }

    /** 平台知识库归属 ID（GLOBAL 助手使用；不是真实用户/课程）。 */
    public static final Long PLATFORM_OWNER_ID = 0L;

    /** 解析归属 key；返回 [type, id字符串]，非法返回 null。 */
    private static String[] parseOwner(String key) {
        if (key == null) return null;
        int i = key.indexOf(':');
        if (i <= 0) return null;
        Long id = parseLongOrNull(key.substring(i + 1));
        if (id == null) return null;
        return new String[]{key.substring(0, i), String.valueOf(id)};
    }

    private static Long parseLongOrNull(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    private void loadDocument(Path p, String owner) {
        try {
            String name = p.getFileName().toString();
            documents.put(name, Files.readString(p));
            docOwner.put(name, owner == null ? "user:-1" : owner);
        } catch (IOException ignored) { }
    }

    // ==================== 私人知识库（USER） ====================

    public Map<String, Object> upload(Long userId, MultipartFile file) throws IOException {
        if (userId == null) throw new IllegalArgumentException("请先登录后再上传知识库文件");
        return uploadTo("user", userId, file);
    }

    // ==================== 课程知识库（COURSE） ====================

    public Map<String, Object> uploadCourse(Long courseId, MultipartFile file) throws IOException {
        if (courseId == null || courseId <= 0) throw new IllegalArgumentException("课程 ID 不合法");
        return uploadTo("course", courseId, file);
    }

    public List<Map<String, Object>> listCourse(Long courseId) {
        if (courseId == null || courseId <= 0) return Collections.emptyList();
        return listByOwner(ownerKey("course", courseId));
    }

    public String courseContent(Long courseId, String id) throws IOException {
        if (courseId == null || courseId <= 0) throw new NoSuchFileException(id);
        String[] owner = parseOwner(docOwner.get(id));
        if (owner == null || !"course".equals(owner[0]) || !String.valueOf(courseId).equals(owner[1]))
            throw new NoSuchFileException(id);
        String value = documents.get(id);
        if (value == null) throw new NoSuchFileException(id);
        return value;
    }

    public void deleteCourse(Long courseId, String id) throws IOException {
        if (courseId == null || courseId <= 0) throw new NoSuchFileException(id);
        String[] owner = parseOwner(docOwner.get(id));
        if (owner == null || !"course".equals(owner[0]) || !String.valueOf(courseId).equals(owner[1]))
            throw new NoSuchFileException(id);
        documents.remove(id);
        docOwner.remove(id);
        Files.deleteIfExists(Paths.get(properties.getDataDir(), "documents", "course", String.valueOf(courseId), id));
        chunkIndex.removeByDoc(id);
        if (vectorStore != null) vectorStore.removeByDoc(id);
    }

    /** 上传通用实现：按 ownerType 划分目录、切块并写入索引/向量库。 */
    private Map<String, Object> uploadTo(String ownerType, Long ownerId, MultipartFile file) throws IOException {
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("document.txt");
        String lower = name.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt")))
            throw new IllegalArgumentException("仅支持 Markdown 或 TXT 文件");
        String safe = UUID.randomUUID() + "-" + Paths.get(name).getFileName();
        String owner = ownerKey(ownerType, ownerId);
        // 同一归属下同名文件只能存在一份
        if (listByOwner(owner).stream().anyMatch(d -> name.equals(d.get("name")))) {
            throw new IllegalArgumentException("知识库中已存在同名文件「" + name + "」，请先删除旧文件或重命名后再上传");
        }
        Path path = Paths.get(properties.getDataDir(), "documents", ownerType, String.valueOf(ownerId), safe);
        Files.createDirectories(path.getParent());
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        Files.writeString(path, text);
        documents.put(safe, text);
        docOwner.put(safe, owner);

        // 结构化切块 + embedding
        List<KnowledgeChunk> chunks = buildChunks(safe, name, text, ownerType, ownerId);
        if (embeddingEnabled) embedChunks(chunks);
        chunkIndex.addAll(safe, name, chunks);
        if (vectorStore != null) vectorStore.addAll(chunks);

        return Map.of("id", safe, "name", name, "size", text.length(), "chunkCount", chunks.size());
    }

    private List<KnowledgeChunk> buildChunks(String docId, String docName, String text, String ownerType, Long ownerId) {
        List<MarkdownChunker.RawChunk> raws = MarkdownChunker.chunk(
                text, docName, properties.getChunkSize(), properties.getChunkOverlap());
        List<KnowledgeChunk> out = new ArrayList<>(raws.size());
        for (MarkdownChunker.RawChunk r : raws) {
            String chunkText = r.text == null ? "" : r.text;
            List<String> kws = MarkdownChunker.keywords(chunkText);
            KnowledgeChunk c = new KnowledgeChunk(
                    UUID.randomUUID().toString(),
                    docId, ownerId, ownerType.toUpperCase(Locale.ROOT), docName,
                    r.sectionPath,
                    r.heading,
                    0, chunkText.length(),
                    chunkText, kws,
                    chunkText.length() / 2,
                    null
            );
            out.add(c);
        }
        return out;
    }

    // ==================== 平台知识库（PLATFORM，GLOBAL 助手使用） ====================

    public Map<String, Object> uploadPlatform(MultipartFile file) throws IOException {
        return uploadTo("platform", PLATFORM_OWNER_ID, file);
    }

    public List<Map<String, Object>> listPlatform() {
        return listByOwner(ownerKey("platform", PLATFORM_OWNER_ID));
    }

    public String platformContent(String id) throws IOException {
        String[] owner = parseOwner(docOwner.get(id));
        if (owner == null || !"platform".equals(owner[0])) throw new NoSuchFileException(id);
        String value = documents.get(id);
        if (value == null) throw new NoSuchFileException(id);
        return value;
    }

    public void deletePlatform(String id) throws IOException {
        String[] owner = parseOwner(docOwner.get(id));
        if (owner == null || !"platform".equals(owner[0])) throw new NoSuchFileException(id);
        documents.remove(id);
        docOwner.remove(id);
        Files.deleteIfExists(Paths.get(properties.getDataDir(), "documents", "platform",
                String.valueOf(PLATFORM_OWNER_ID), id));
        chunkIndex.removeByDoc(id);
        if (vectorStore != null) vectorStore.removeByDoc(id);
    }

    // ==================== 文件管理（USER） ====================

    public int chunkTotal(Long userId) {
        return (int) chunkIndex.all().stream()
                .filter(c -> "user".equals(ownerTypeOf(c)) && userId != null && userId.equals(c.getUserId()))
                .count();
    }

    private static String ownerTypeOf(KnowledgeChunk c) {
        String t = c.getOwnerType();
        if (t != null && !t.isBlank()) return t.toLowerCase(Locale.ROOT);
        return "user"; // 历史数据默认 USER
    }

    public List<Map<String, Object>> list(Long userId) {
        if (userId == null) return Collections.emptyList();
        return listByOwner(ownerKey("user", userId));
    }

    /** 列出某归属下全部文件（按 safe 名解析显示名）。 */
    private List<Map<String, Object>> listByOwner(String owner) {
        List<Map<String, Object>> result = new ArrayList<>();
        documents.forEach((name, text) -> {
            if (!owner.equals(docOwner.get(name))) return;
            int separator = name.length() > 37 && name.charAt(36) == '-' ? 36 : name.indexOf('-');
            String displayName = separator >= 0 && separator + 1 < name.length() ? name.substring(separator + 1) : name;
            int chunkCount = (int) chunkIndex.all().stream().filter(c -> name.equals(c.getDocId())).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", name);
            item.put("name", displayName);
            item.put("size", text.length());
            item.put("chunkCount", chunkCount);
            result.add(item);
        });
        return result;
    }

    public String content(Long userId, String id) throws IOException {
        if (userId == null || !ownerKey("user", userId).equals(docOwner.get(id))) throw new NoSuchFileException(id);
        String value = documents.get(id);
        if (value == null) throw new NoSuchFileException(id);
        return value;
    }

    public void delete(Long userId, String id) throws IOException {
        if (userId == null || !ownerKey("user", userId).equals(docOwner.get(id))) throw new NoSuchFileException(id);
        documents.remove(id);
        docOwner.remove(id);
        Files.deleteIfExists(Paths.get(properties.getDataDir(), "documents", "user", String.valueOf(userId), id));
        chunkIndex.removeByDoc(id);
        if (vectorStore != null) vectorStore.removeByDoc(id);
    }

    // ==================== 会话（委托 ChatSessionStore） ====================

    public List<Map<String, Object>> sessions(Long userId, String assistantType) {
        return sessionStore.listSessions(userId, assistantType);
    }

    public Map<String, Object> createSession(Long userId, String name, String tag, String assistantType, Long courseId) {
        if (userId == null) throw new IllegalArgumentException("请先登录后再创建会话");
        if (ChatSessionStore.TYPE_COURSE.equals(assistantType) && (courseId == null || courseId <= 0))
            throw new IllegalArgumentException("课程会话必须携带课程 ID");
        return sessionStore.createSession(userId, name, tag, assistantType, courseId);
    }

    public void deleteSession(Long userId, String id, String assistantType) {
        sessionStore.deleteSession(userId, id, assistantType);
    }

    public List<Map<String, Object>> records(Long userId, String sessionId, String assistantType) {
        return sessionStore.listRecords(userId, sessionId, assistantType);
    }

    public Map<String, Object> updateSession(Long userId, String id, String name, String tag, String assistantType) {
        sessionStore.updateSession(userId, id, name, tag, assistantType);
        return sessionStore.ownSession(userId, id, assistantType);
    }

    // ==================== 聊天 ====================

    /** 归一化助手类型；非法值默认 PRIVATE。 */
    public static String normalizeType(String type) {
        if (type == null) return ChatSessionStore.TYPE_PRIVATE;
        String t = type.trim().toUpperCase(Locale.ROOT);
        if (ChatSessionStore.TYPE_GLOBAL.equals(t)) return ChatSessionStore.TYPE_GLOBAL;
        if (ChatSessionStore.TYPE_COURSE.equals(t)) return ChatSessionStore.TYPE_COURSE;
        return ChatSessionStore.TYPE_PRIVATE;
    }

    /**
     * 聊天并返回结构化结果：回答正文 + 本次检索命中的知识来源。
     * 用于前端「引用标注」——让用户能追溯答案来自哪份资料、哪一节。
     * 注：原非流式 chat(...) 两个重载已随 GET /file/chat 接口一并移除（无调用方，
     * 且与 /chat/simple 的 chatWithSources 重复）。
     */
    public Map<String, Object> chatWithSources(String sessionId, Long userId, String assistantType,
                                               Long courseId, String question) {
        String type = normalizeType(assistantType);
        String query = question == null ? "" : question.trim();
        List<KnowledgeChunk> ctx = retrieveFor(type, userId, courseId, query);
        String answer = answerWithContext(ctx, query, type, courseId);
        sessionStore.appendRound(userId, sessionId, type, query, answer);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", answer);
        out.put("sources", toSources(ctx));
        out.put("assistantType", type);
        return out;
    }

    /**
     * 私人助手「学习工具」的非流式版本（供出题这类需要完整 JSON 的场景使用）。
     * 与 streamTask 共用同一套检索与模板。
     */
    public Map<String, Object> chatTask(String sessionId, Long userId, String task, String message) {
        return chatTask(sessionId, userId, task, message, null);
    }

    /**
     * 私人助手「学习工具」的非流式版本。
     *
     * @param docId 资料范围：某份文件的 docId → 只处理该文件；null / 空 / "all" → 处理全部文件
     */
    public Map<String, Object> chatTask(String sessionId, Long userId, String task, String message, String docId) {
        String normalized = normalizeTask(task);
        if (normalized == null) throw new IllegalArgumentException("不支持的任务类型：" + task);
        String query = message == null ? "" : message.trim();
        List<KnowledgeChunk> ctx = taskContext(userId, docId);
        String answer = model == null ? localTaskAnswer(ctx, normalized) : model.generate(buildTaskPrompt(normalized, query, ctx));
        sessionStore.appendRound(userId, sessionId, ChatSessionStore.TYPE_PRIVATE, query, answer);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", answer);
        out.put("sources", toSources(ctx));
        out.put("assistantType", ChatSessionStore.TYPE_PRIVATE);
        out.put("task", normalized);
        return out;
    }

    /**
     * 把检索命中的 chunk 转成前端可展示的引用来源，按「文档 + 章节」去重。
     */
    public static List<Map<String, Object>> toSources(List<KnowledgeChunk> ctx) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (ctx == null || ctx.isEmpty()) return out;
        Set<String> seen = new HashSet<>();
        for (KnowledgeChunk c : ctx) {
            if (c == null) continue;
            String doc = c.getDocName() == null ? "" : c.getDocName();
            // 「覆盖范围说明」是提示词专用说明，不作为引用来源展示
            if (SCOPE_NOTICE_DOC.equals(doc)) continue;
            String section = sectionLabel(c);
            if (!seen.add(doc + "|" + section)) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("docName", doc);
            item.put("section", section);
            item.put("snippet", snippetOf(c.getText()));
            out.add(item);
        }
        return out;
    }

    /** 章节路径展示名，如「第一章 Java 集合框架 > 1.2 List 集合」。 */
    private static String sectionLabel(KnowledgeChunk c) {
        List<String> path = c.getSectionPath();
        if (path != null && !path.isEmpty()) return String.join(" > ", path);
        return c.getHeading() == null ? "" : c.getHeading();
    }

    /** 取片段首行的纯文本摘要，作为引用条目的一行说明。 */
    private static String snippetOf(String text) {
        if (text == null) return "";
        for (String line : text.split("\\r?\\n")) {
            String t = line.replaceAll("^[#>*\\-\\s]+", "").trim();
            if (t.isEmpty()) continue;
            return t.length() > 60 ? t.substring(0, 60) + "…" : t;
        }
        return "";
    }

    /** Streams provider tokens while retaining the local fallback for offline demos. */
    public void streamChat(String sessionId, Long userId, String assistantType, Long courseId, String question,
                           Consumer<String> onToken, Consumer<String> onComplete, Consumer<Throwable> onError) {
        streamChat(sessionId, userId, assistantType, courseId, question, onToken, onComplete, onError, null);
    }

    /**
     * 流式聊天。onSources 在检索完成后、首个 token 之前回调一次，用于向前端推送引用来源。
     */
    public void streamChat(String sessionId, Long userId, String assistantType, Long courseId, String question,
                           Consumer<String> onToken, Consumer<String> onComplete, Consumer<Throwable> onError,
                           Consumer<List<Map<String, Object>>> onSources) {
        streamInternal(sessionId, userId, assistantType, courseId, question, null, null,
                onToken, onComplete, onError, onSources);
    }

    /**
     * 流式执行私人助手的「学习工具」任务（笔记整理 / 内容检查 / 自动出题 / 学习计划）。
     * 资料范围默认「全部文件」；需要只处理某一份时用带 docId 的重载。
     */
    public void streamTask(String sessionId, Long userId, String task, String message,
                           Consumer<String> onToken, Consumer<String> onComplete, Consumer<Throwable> onError,
                           Consumer<List<Map<String, Object>>> onSources) {
        streamTask(sessionId, userId, task, message, null, onToken, onComplete, onError, onSources);
    }

    /**
     * 流式执行私人助手的「学习工具」任务，可指定资料范围。
     *
     * @param docId 某份文件的 docId → 只处理该文件；null / 空 / "all" → 处理全部文件
     */
    public void streamTask(String sessionId, Long userId, String task, String message, String docId,
                           Consumer<String> onToken, Consumer<String> onComplete, Consumer<Throwable> onError,
                           Consumer<List<Map<String, Object>>> onSources) {
        String normalized = normalizeTask(task);
        if (normalized == null) throw new IllegalArgumentException("不支持的任务类型：" + task);
        streamInternal(sessionId, userId, ChatSessionStore.TYPE_PRIVATE, null, message, normalized, docId,
                onToken, onComplete, onError, onSources);
    }

    /** 流式内部实现：task 为空走按助手类型的通用提示词，task 非空走「学习工具」模板。 */
    private void streamInternal(String sessionId, Long userId, String assistantType, Long courseId, String question,
                                String task, String docId,
                                Consumer<String> onToken, Consumer<String> onComplete, Consumer<Throwable> onError,
                                Consumer<List<Map<String, Object>>> onSources) {
        String type = normalizeType(assistantType);
        String query = question == null ? "" : question.trim();
        // 学习工具按「资料范围」取全量文本；普通问答仍走向量/关键词检索
        List<KnowledgeChunk> ctx = task != null ? taskContext(userId, docId) : retrieveFor(type, userId, courseId, query);
        if (onSources != null) {
            try { onSources.accept(toSources(ctx)); } catch (Throwable ignored) { }
        }
        if (streamingModel == null) {
            String answer = task != null ? localTaskAnswer(ctx, task) : localAnswer(ctx, type);
            CompletableFuture.runAsync(() -> {
                try {
                    for (int start = 0; start < answer.length(); start += 12) {
                        onToken.accept(answer.substring(start, Math.min(start + 12, answer.length())));
                        Thread.sleep(18L);
                    }
                    sessionStore.appendRound(userId, sessionId, type, query, answer);
                    onComplete.accept(answer);
                } catch (Throwable error) {
                    onError.accept(error);
                }
            });
            return;
        }
        StringBuilder answer = new StringBuilder();
        try {
            streamingModel.generate(task != null ? buildTaskPrompt(task, query, ctx) : buildPrompt(query, ctx, type, courseId), new StreamingResponseHandler<AiMessage>() {
                @Override public void onNext(String token) {
                    if (token == null || token.isEmpty()) return;
                    answer.append(token);
                    onToken.accept(token);
                }

                @Override public void onComplete(dev.langchain4j.model.output.Response<AiMessage> response) {
                    String complete = answer.toString();
                    sessionStore.appendRound(userId, sessionId, type, query, complete);
                    onComplete.accept(complete);
                }

                @Override public void onError(Throwable error) { onError.accept(error); }
            });
        } catch (Throwable error) {
            onError.accept(error);
        }
    }

    /**
     * 按助手类型决定检索范围：
     *   GLOBAL  → 平台知识库（platform:0，课程目录/平台功能/购买流程等）
     *   COURSE  → 课程知识库（course:{courseId}）
     *   PRIVATE → 当前用户私人知识库（user:{userId}）
     */
    private List<KnowledgeChunk> retrieveFor(String type, Long userId, Long courseId, String query) {
        String ownerType;
        Long ownerId;
        if (ChatSessionStore.TYPE_GLOBAL.equals(type)) {
            ownerType = "platform";
            ownerId = PLATFORM_OWNER_ID;
        } else if (ChatSessionStore.TYPE_COURSE.equals(type)) {
            ownerType = "course";
            ownerId = courseId;
        } else {
            ownerType = "user";
            ownerId = userId;
        }
        if (ownerId == null) return Collections.emptyList();
        return retrieve(ownerType, ownerId, query);
    }

    // ==================== 学习工具的资料范围 ====================

    /** 「全部资料」哨兵值：前端下拉选中「全部资料」时按全部文件处理。 */
    public static final String SCOPE_ALL = "all";
    /** 单份文件在提示词中的字符预算上限。 */
    private static final int TASK_DOC_MAX_CHARS = 12000;
    /** 全部文件合计的字符预算上限。 */
    private static final int TASK_TOTAL_MAX_CHARS = 24000;
    /** 「全部」模式下均摊后单个文件的预算下限。 */
    private static final int TASK_MIN_DOC_CHARS = 800;
    /** 预算不足、未能覆盖全部文件时，追加在上下文末尾的说明 chunk 的 docName。 */
    private static final String SCOPE_NOTICE_DOC = "（覆盖范围说明）";

    /**
     * 学习工具（笔记整理 / 内容检查 / 自动出题 / 学习计划）的资料上下文。
     *
     * <p>为什么不用 RAG：普通问答只需要「与这句话最相关的片段」，所以走向量 topK；
     * 但工具类任务的产物必须覆盖<b>整份资料</b>。用户上传多份笔记时，topK 会把几份笔记的
     * 片段混在一起，既无法保证覆盖完整，也说不清这次到底在整理哪一份。</p>
     *
     * <p>因此这里按「文件」直接取全文：</p>
     * <ul>
     *   <li>{@code docId} 指定某一份 → 只取该文件全文；</li>
     *   <li>{@code docId} 为空或 {@code all} → 取该用户全部文件，按文件名排序、逐份标注。</li>
     * </ul>
     *
     * <p>每份文件与总量都有字符预算：单份上限 {@link #TASK_DOC_MAX_CHARS}，总量上限
     * {@link #TASK_TOTAL_MAX_CHARS}，超出按预算截断并标注，避免提示词无限膨胀。</p>
     */
    private List<KnowledgeChunk> taskContext(Long userId, String docId) {
        if (userId == null) return Collections.emptyList();
        List<Map<String, Object>> files = listByOwner(ownerKey("user", userId));
        if (files.isEmpty()) return Collections.emptyList();
        // listByOwner 遍历的是 ConcurrentHashMap，顺序不稳定；按文件名排序保证结果可复现
        files.sort(Comparator.comparing(f -> String.valueOf(f.get("name"))));

        String wanted = docId == null ? "" : docId.trim();
        boolean single = !wanted.isEmpty() && !SCOPE_ALL.equalsIgnoreCase(wanted);
        if (single) files.removeIf(f -> !wanted.equals(String.valueOf(f.get("id"))));
        if (files.isEmpty()) return Collections.emptyList();

        // 单份：整份尽量给足预算；全部：按文件数均摊，避免靠前的文件把总预算吃光
        int perDoc = single
                ? TASK_TOTAL_MAX_CHARS
                : Math.max(TASK_MIN_DOC_CHARS, Math.min(TASK_DOC_MAX_CHARS, TASK_TOTAL_MAX_CHARS / files.size()));

        List<KnowledgeChunk> out = new ArrayList<>();
        List<String> included = new ArrayList<>();
        int used = 0;
        for (Map<String, Object> f : files) {
            if (used >= TASK_TOTAL_MAX_CHARS) break;
            String id = String.valueOf(f.get("id"));
            String name = String.valueOf(f.get("name"));
            String text = documents.get(id);
            if (text == null || text.isBlank()) continue;
            String body = clip(text, Math.min(perDoc, TASK_TOTAL_MAX_CHARS - used));
            if (body.isEmpty()) continue;
            used += body.length();
            included.add(name);
            // 每份文件合成一个 chunk：sectionPath 留空 → 提示词与引用来源都以「文件名」为单位标注
            out.add(new KnowledgeChunk("task-" + id, id, userId, "USER", name,
                    null, null, 0, body.length(), body, null, body.length() / 2, null));
        }
        // 预算不足时明确告知模型「本次只覆盖了这些文件」，避免它把部分覆盖说成整理完了全部资料
        if (!out.isEmpty() && included.size() < files.size()) {
            List<String> missed = new ArrayList<>();
            for (Map<String, Object> f : files) {
                String name = String.valueOf(f.get("name"));
                if (!included.contains(name)) missed.add(name);
            }
            String notice = "本次因上下文长度限制，只提供了以下 " + included.size() + " 份文件："
                    + String.join("、", included) + "；未包含的文件有：" + String.join("、", missed)
                    + "。请在回答开头用一句话说明本次整理覆盖了哪些文件。";
            out.add(new KnowledgeChunk("task-notice", "task-notice", userId, "USER", SCOPE_NOTICE_DOC,
                    null, null, 0, notice.length(), notice, null, notice.length() / 2, null));
        }
        return out;
    }

    /** 截断到 len 个字符；超出时追加截断标记，让模型知道资料被裁剪过。 */
    private static String clip(String text, int len) {
        if (text == null) return "";
        String t = text.strip();
        if (len <= 0) return "";
        return t.length() <= len ? t : t.substring(0, len) + "\n\n（本文件内容较长，已截断）";
    }

    /** 上下文里出现的文件 id 集合（用于判断是否多份资料）。 */
    private static Set<String> docIdsOf(List<KnowledgeChunk> ctx) {
        Set<String> ids = new LinkedHashSet<>();
        if (ctx != null) {
            for (KnowledgeChunk c : ctx) {
                if (c == null || c.getDocId() == null) continue;
                if (SCOPE_NOTICE_DOC.equals(c.getDocName())) continue; // 说明 chunk 不算一份资料
                ids.add(c.getDocId());
            }
        }
        return ids;
    }

    /** 检索：embedding 启用时走向量 topK，未启用或失败时走关键词兜底。仅检索指定归属的 chunk。 */
    private List<KnowledgeChunk> retrieve(String ownerType, Long ownerId, String query) {
        if (query.isEmpty() || chunkIndex.isEmpty()) return Collections.emptyList();
        List<KnowledgeChunk> mine = new ArrayList<>();
        for (KnowledgeChunk c : chunkIndex.all()) {
            if (ownerType.equals(ownerTypeOf(c)) && ownerId.equals(c.getUserId())) mine.add(c);
        }
        if (mine.isEmpty()) return Collections.emptyList();
        if (embeddingEnabled) {
            try {
                float[] q = embed(query);
                List<KnowledgeChunk> out = new ArrayList<>();
                // pgvector 可用时由向量库做 ANN 检索（向量召回后再按归属过滤）
                if (vectorStore != null && vectorStore.isAvailable()) {
                    Map<String, KnowledgeChunk> byId = new HashMap<>();
                    for (KnowledgeChunk c : mine) byId.put(c.getChunkId(), c);
                    int candidateK = Math.max(properties.getTopK(), properties.getRerankCandidateK());
                    // 归属过滤下推到向量检索：只在本课程/本用户的数据里做 ANN，避免被其它归属挤占召回
                    for (PgVectorStore.ScoredId s : vectorStore.search(q, candidateK, ownerType, ownerId)) {
                        KnowledgeChunk c = byId.get(s.chunkId);
                        if (c != null) out.add(c);
                    }
                } else {
                    // 本地 cosine 内存检索（pgvector 不可用时的降级）
                    int k = Math.max(properties.getTopK(), properties.getRerankCandidateK());
                    k = Math.min(k, mine.size());
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
                // 关键词兜底追加（去重，仅本归属）
                Set<String> seen = new HashSet<>();
                for (KnowledgeChunk c : out) seen.add(c.getChunkId());
                for (KnowledgeChunk c : mine) {
                    if (keywordHit(c, query) && seen.add(c.getChunkId())) out.add(c);
                }
                return rerank(query, out);
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

    /**
     * 检索结果重排：把候选 chunk 按 query 相关性重新打分排序，取前 rerankTopN。
     * 未启用 rerank 或调用失败时，按原顺序截取前 topK 条返回（不破坏现有行为）。
     */
    private List<KnowledgeChunk> rerank(String query, List<KnowledgeChunk> candidates) {
        if (candidates == null || candidates.isEmpty()) return candidates;
        if (!rerankClient.isEnabled()) {
            return candidates.size() <= properties.getTopK()
                    ? candidates
                    : candidates.subList(0, properties.getTopK());
        }
        int topN = Math.min(properties.getRerankTopN(), candidates.size());
        List<String> docs = new ArrayList<>(candidates.size());
        for (KnowledgeChunk c : candidates) docs.add(c.getText() == null ? "" : c.getText());
        List<Integer> order = rerankClient.rerank(query, docs, topN);
        if (order == null || order.isEmpty()) {
            return candidates.size() <= topN ? candidates : candidates.subList(0, topN);
        }
        List<KnowledgeChunk> out = new ArrayList<>(order.size());
        for (Integer idx : order) {
            if (idx != null && idx >= 0 && idx < candidates.size()) out.add(candidates.get(idx));
            if (out.size() >= topN) break;
        }
        if (out.isEmpty()) {
            return candidates.size() <= topN ? candidates : candidates.subList(0, topN);
        }
        return out;
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

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) return 0d;
        double dot = 0d, na = 0d, nb = 0d;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        if (na == 0d) return 0d;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private String answerWithContext(List<KnowledgeChunk> ctx, String question, String type, Long courseId) {
        if (model == null) return localAnswer(ctx, type);
        return model.generate(buildPrompt(question, ctx, type, courseId));
    }

    /** 按助手类型生成不同的系统提示词。 */
    /**
     * 通用输出格式要求。
     * 中文模型为了省 token 常写出紧凑 Markdown（`##标题`、`-内容`、`>说明`、`|a|b|`），
     * CommonMark 不识别这些写法，页面上会直接显示 `##`、`-`、`>`、`|` 等原始符号。
     * 前端另有 normalizeMarkdown 兜底，这里从源头要求模型输出规范 Markdown。
     */
    private static final String FORMAT_RULES =
            "\n【输出格式要求】\n" +
            "- 使用标准 Markdown：`#` 与 `-` 后面必须留一个空格（写成「## 标题」而不是「##标题」）；\n" +
            "- 不要使用「>」引用符号，也不要使用 HTML 标签（如 blockquote、br 等）；\n" +
            "- 需要强调时用 **加粗**，不要用其它标记符号；\n" +
            "- 如果使用表格，必须有表头分隔行（如 `|---|---|`），否则表格不会正常显示。\n";

    /** 简洁性要求：避免回答过长、答非所问地铺开所有方向。 */
    private static final String BREVITY_RULES =
            "\n【简洁要求】\n" +
            "- 直接回答用户所问，不要复述用户的话；\n" +
            "- 用户点名了某个方向（例如 Java）时，就只讲该方向，不要顺带罗列其它方向；\n" +
            "- 不要使用表格，需要罗列时用短列表，一行一条；\n" +
            "- 整段回答尽量控制在 300 字以内（列表项不计入）；能一两句说清就不要写成一段。\n";

    private String buildPrompt(String question, List<KnowledgeChunk> ctx, String type, Long courseId) {
        StringBuilder sb = new StringBuilder();
        if (ChatSessionStore.TYPE_GLOBAL.equals(type)) {
            sb.append("你是「知序学堂」的平台级AI助手，面向全体学员，负责平台层面的引导。你的职责：\n")
              .append("1. 推荐平台课程、规划学习路线（例如 Java 学习路线、Python 学习路线）；\n")
              .append("2. 说明平台功能与使用流程（如何选课、如何购买课程、如何学习/考试、积分与优惠券等）；\n")
              .append("3. 解答与平台使用相关的常规问题。\n")
              .append("【重要限制】你不负责深入讲解具体课程的知识点（如某个语法细节、某个框架的内部原理、某段代码为什么这么写）。")
              .append("当用户提出这类课程知识问题时，请勿展开讲解，而应简洁地说明：可在对应课程的详情页使用「AI助教」获得基于该课程资料的回答，并尽量给出具体课程名或引导用户到课程列表找到该课程。\n")
              .append("涉及平台的课程、分类、价格、节数等信息时，必须严格依据下方【平台课程目录】与平台资料中真实存在的课程来回答，不要编造不存在的课程。")
              .append("回答要有引导性。")
              .append(BREVITY_RULES)
              .append(FORMAT_RULES)
              .append("\n平台资料：\n");
        } else if (ChatSessionStore.TYPE_COURSE.equals(type)) {
            sb.append("你是「知序学堂」的课程AI助教");
            if (courseId != null) sb.append("（当前课程 ID：").append(courseId).append("）");
            sb.append("。请优先依据本课程参考资料回答；资料涉及相关概念时，可用简洁的基础知识补充解释，不要编造与问题无关的内容。")
              .append("资料确实没有涉及时，再明确说明。")
              .append(FORMAT_RULES)
              .append("\n参考资料：\n");
        } else {
            sb.append("你是「知序学堂」用户的私人AI助手。请优先依据用户上传的参考资料回答；")
              .append("资料涉及相关概念时，可用简洁的基础知识补充解释，不要编造与问题无关的内容。")
              .append("资料确实没有涉及时，直接用你的知识回答即可。")
              .append(FORMAT_RULES)
              .append("\n参考资料：\n");
        }
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

    // ==================== 私人助手「学习工具」任务模板 ====================

    /** 学习工具任务名。 */
    public static final String TASK_SUMMARY = "summary";
    public static final String TASK_CHECK = "check";
    public static final String TASK_QUIZ = "quiz";
    public static final String TASK_PLAN = "plan";

    /** 归一化任务名；非法或为空返回 null。 */
    public static String normalizeTask(String task) {
        if (task == null) return null;
        String t = task.trim().toLowerCase(Locale.ROOT);
        if (TASK_SUMMARY.equals(t) || TASK_CHECK.equals(t) || TASK_QUIZ.equals(t) || TASK_PLAN.equals(t)) return t;
        return null;
    }

    /** 按「学习工具」任务生成系统提示词；四种任务共用同一套个人知识库检索结果。 */
    private String buildTaskPrompt(String task, String question, List<KnowledgeChunk> ctx) {
        StringBuilder sb = new StringBuilder();
        if (TASK_SUMMARY.equals(task)) {
            sb.append("你是「知序学堂」用户的私人AI学习助手，现在执行【笔记整理】任务。\n");
            if (docIdsOf(ctx).size() > 1) {
                // 「全部资料」且确实有多份：先分文件再展开，否则多份笔记会被揉成一份，用户看不出整理了哪些
                sb.append("本次参考资料共 ").append(docIdsOf(ctx).size())
                  .append(" 份文件，请**逐份整理**：每份文件先用「## 文件：<文件名>」作为小标题锁定文件边界，")
                  .append("再在该小标题下展开下面三部分，不同文件的内容不要混在一起。\n")
                  .append("### 一、知识体系\n用层级清晰的大纲，梳理该文件覆盖的知识点及其从属关系。\n")
                  .append("### 二、思维导图\n用 Markdown 嵌套列表（- 与缩进）表示思维导图结构；不要输出 mermaid 代码块。\n")
                  .append("### 三、核心要点总结\n用 3-6 条要点概括该文件的核心内容，每条一句话。\n")
                  .append("全部文件整理完后，再用「## 整体关联」用一两句话说明这几份资料之间的知识联系。\n");
            } else {
                sb.append("请阅读下方「参考资料」（用户上传的个人笔记/资料），输出一份结构化整理结果，包含三部分：\n")
                  .append("## 一、知识体系\n用层级清晰的大纲，梳理资料覆盖的知识点及其从属关系。\n")
                  .append("## 二、思维导图\n用 Markdown 嵌套列表（- 与缩进）表示思维导图结构，从主题逐层展开；不要输出 mermaid 代码块。\n")
                  .append("## 三、核心要点总结\n用 5-8 条要点概括资料核心内容，每条一句话。\n");
            }
            sb.append("要求：只依据参考资料，不要编造资料中没有的内容；某部分资料不足时如实说明。请用 Markdown 格式。\n");
        } else if (TASK_CHECK.equals(task)) {
            sb.append("你是「知序学堂」用户的私人AI学习助手，现在执行【内容检查】任务。\n")
              .append("请通读下方「参考资料」（用户上传的知识笔记），检查**资料内容本身**有没有问题，重点看四类：\n")
              .append("1. 知识点错误：概念、结论、公式、代码等写错；\n")
              .append("2. 过时或不严谨的表述；\n")
              .append("3. 前后矛盾、重复、逻辑不通；\n")
              .append("4. 关键内容缺失，导致按这份资料学下来会理解不完整。\n");
            if (docIdsOf(ctx).size() > 1) {
                // 多份资料时按文件分别检查，否则用户看不出问题出在哪一份
                sb.append("本次参考资料共 ").append(docIdsOf(ctx).size())
                  .append(" 份文件，请**逐份检查**：每份文件先用「## 文件：<文件名>」作为小标题锁定文件边界，")
                  .append("再在该小标题下输出这一份的检查结果，不同文件的问题不要混在一起。\n");
            }
            sb.append("输出要求：\n")
              .append("1. 用 Markdown 表格列出：| 问题原文（摘录） | 问题类型 | 说明与修正建议 |；摘录取能定位到问题的短句即可，不用整段照抄。\n")
              .append("2. 表格之后给出总体评价：这份资料整体质量如何，最该优先修正的是哪几点。\n")
              .append("3. 如果确实没发现问题，直接说明「未发现明显问题」，再补充 1-3 条可以完善的地方；不要为了凑数硬找问题。\n")
              .append("要求：只依据「参考资料」本身判断，不要编造资料中没有的内容；对没有把握的专业结论标注「存疑，建议核实」。\n");
        } else if (TASK_QUIZ.equals(task)) {
            sb.append("你是「知序学堂」用户的私人AI学习助手，现在执行【自动生成题目】任务。\n")
              .append("请基于「参考资料」生成知识检测题（题量以用户问题中的要求为准，未说明时默认 3 道），题型为单选题或判断题。\n")
              .append("【输出格式】必须且只能输出一个 JSON 对象，不要输出任何解释文字，不要使用 Markdown 代码块围栏。格式：\n")
              .append("{\"questions\":[{\"type\":\"single\",\"stem\":\"题干\",\"options\":[\"选项A\",\"选项B\",\"选项C\",\"选项D\"],\"answerIndex\":0,\"explanation\":\"解析\",\"point\":\"知识点\"}]}\n")
              .append("其中 type 取 \"single\"（单选，4 个选项）或 \"bool\"（判断，options 固定为 [\"正确\",\"错误\"]）；")
              .append("answerIndex 为正确选项下标（从 0 开始）；题干、选项与解析必须来自参考资料，不要编造；")
              .append("正确答案位置请随机分布，不要每道题都放在第一个。\n");
        } else {
            sb.append("你是「知序学堂」用户的私人AI学习助手，现在执行【学习计划制定】任务。\n")
              .append("请结合用户在下方问题中给出的学习目标与时间，以及「参考资料」覆盖的知识范围，制定一份可执行的学习计划。\n")
              .append("输出要求：\n")
              .append("1. 用 Markdown 表格给出计划：| 阶段/天数 | 学习主题 | 学习目标 | 建议产出或练习 |。\n")
              .append("2. 表格之后给出 3-5 条执行建议（如何检验效果、如何复习）。\n")
              .append("要求：计划必须落在参考资料覆盖的知识范围内；用户未给出时长时，默认按 7 天安排。\n");
        }
        sb.append("参考资料：\n");
        appendContext(sb, ctx);
        sb.append("问题：").append(question);
        return sb.toString();
    }

    /**
     * 把检索到的 chunk 以 [来源] 正文 的形式追加进提示词。
     *
     * <p>学习工具的资料是按「文件」整体取全文（sectionPath 为空），此时改用
     * {@code 【文件：xxx】} 单独成行分组标注，模型才能看出每段内容属于哪一份笔记。</p>
     */
    private static void appendContext(StringBuilder sb, List<KnowledgeChunk> ctx) {
        if (ctx == null || ctx.isEmpty()) {
            sb.append("（无）\n");
            return;
        }
        for (KnowledgeChunk c : ctx) {
            boolean hasPath = c.getSectionPath() != null && !c.getSectionPath().isEmpty();
            if (hasPath) {
                sb.append("[").append(String.join(" > ", c.getSectionPath())).append("] ");
            } else {
                sb.append("【文件：").append(c.getDocName() == null ? "未命名" : c.getDocName()).append("】\n");
            }
            sb.append(c.getText()).append("\n\n");
        }
    }

    /** 未接入大模型时的任务降级回答。 */
    private String localTaskAnswer(List<KnowledgeChunk> ctx, String task) {
        if (ctx == null || ctx.isEmpty()) return "个人知识库中没有找到与该任务相关的内容，请先上传资料。";
        return localAnswer(ctx, ChatSessionStore.TYPE_PRIVATE);
    }

    private String localAnswer(List<KnowledgeChunk> ctx, String type) {
        if (ctx == null || ctx.isEmpty()) {
            if (ChatSessionStore.TYPE_GLOBAL.equals(type)) return "（本地演示模式，未接入大模型）";
            return "知识库中没有找到与该问题相关的内容。";
        }
        StringBuilder sb = new StringBuilder("已检索到相关知识片段：\n\n");
        for (KnowledgeChunk c : ctx) {
            String ref = (c.getSectionPath() == null || c.getSectionPath().isEmpty())
                    ? c.getDocName()
                    : String.join(" > ", c.getSectionPath());
            sb.append("[").append(ref).append("] ").append(c.getText()).append("\n\n");
        }
        return sb.toString();
    }

    private static final class Scored {
        final KnowledgeChunk chunk;
        final double score;
        Scored(KnowledgeChunk c, double s) { this.chunk = c; this.score = s; }
        double score() { return score; }
    }
}
