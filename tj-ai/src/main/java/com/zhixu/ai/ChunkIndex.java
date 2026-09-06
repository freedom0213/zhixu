package com.zhixu.ai;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * chunk 索引：内存里有所有 chunk（带 embedding 可选），落盘到 chunks.json。
 * 同时维护一个关键词倒排表用于 embedding 失败时的降级检索。
 */
public class ChunkIndex {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** chunkId -> chunk */
    private final Map<String, KnowledgeChunk> chunks = new ConcurrentHashMap<>();
    /** 关键词 -> chunkId 集合（用于兜底检索） */
    private final Map<String, Set<String>> inverted = new ConcurrentHashMap<>();
    /** docId -> chunkIds（用于删除整文档时清掉 chunk） */
    private final Map<String, Set<String>> byDoc = new ConcurrentHashMap<>();

    private final Path indexFile;

    public ChunkIndex(Path indexFile) {
        this.indexFile = indexFile;
        load();
    }

    public synchronized void addAll(String docId, String docName, List<KnowledgeChunk> list) {
        if (list == null || list.isEmpty()) return;
        Set<String> ids = byDoc.computeIfAbsent(docId, k -> ConcurrentHashMap.newKeySet());
        for (KnowledgeChunk c : list) {
            chunks.put(c.getChunkId(), c);
            ids.add(c.getChunkId());
            for (String kw : c.getKeywords()) {
                inverted.computeIfAbsent(kw, k -> ConcurrentHashMap.newKeySet()).add(c.getChunkId());
            }
        }
        persist();
    }

    public synchronized void removeByDoc(String docId) {
        Set<String> ids = byDoc.remove(docId);
        if (ids == null) return;
        for (String cid : ids) {
            KnowledgeChunk c = chunks.remove(cid);
            if (c != null && c.getKeywords() != null) {
                for (String kw : c.getKeywords()) {
                    Set<String> set = inverted.get(kw);
                    if (set != null) set.remove(cid);
                }
            }
        }
        persist();
    }

    public List<KnowledgeChunk> all() {
        return new ArrayList<>(chunks.values());
    }

    public int size() {
        return chunks.size();
    }

    /** 关键词命中：返回前 limit 条 chunk。 */
    public List<KnowledgeChunk> keywordSearch(String query, int limit) {
        if (query == null || query.isEmpty() || limit <= 0) return Collections.emptyList();
        Set<String> hits = new LinkedHashSet<>();
        String lower = query.toLowerCase(Locale.ROOT);
        // 英文 token
        Matcher tokenM = Pattern.compile("[a-z0-9]{2,}").matcher(lower);
        while (tokenM.find()) {
            Set<String> set = inverted.get(tokenM.group());
            if (set != null) hits.addAll(set);
        }
        // 中文 2-gram
        for (int i = 0; i + 1 < lower.length(); i++) {
            char a = lower.charAt(i), b = lower.charAt(i + 1);
            if (a > 127 && b > 127) {
                Set<String> set = inverted.get(lower.substring(i, i + 2));
                if (set != null) hits.addAll(set);
            }
        }
        List<KnowledgeChunk> out = new ArrayList<>();
        for (String cid : hits) {
            KnowledgeChunk c = chunks.get(cid);
            if (c != null) out.add(c);
            if (out.size() >= limit) break;
        }
        return out;
    }

    public boolean hasEmbeddings() {
        for (KnowledgeChunk c : chunks.values()) {
            if (c.getEmbedding() != null && c.getEmbedding().length > 0) return true;
        }
        return false;
    }

    public boolean isEmpty() { return chunks.isEmpty(); }

    private void load() {
        if (indexFile == null || !Files.exists(indexFile)) return;
        try {
            List<?> raw = MAPPER.readValue(indexFile.toFile(), List.class);
            for (Object o : raw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                KnowledgeChunk c = MAPPER.convertValue(m, KnowledgeChunk.class);
                chunks.put(c.getChunkId(), c);
                byDoc.computeIfAbsent(c.getDocId(), k -> ConcurrentHashMap.newKeySet()).add(c.getChunkId());
                if (c.getKeywords() != null) {
                    for (String kw : c.getKeywords()) {
                        inverted.computeIfAbsent(kw, k -> ConcurrentHashMap.newKeySet()).add(c.getChunkId());
                    }
                }
            }
        } catch (IOException ignored) { }
    }

    private void persist() {
        if (indexFile == null) return;
        try {
            Path parent = indexFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            List<KnowledgeChunk> snapshot = new ArrayList<>(chunks.values());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(indexFile.toFile(), snapshot);
        } catch (IOException ignored) { }
    }
}
