package com.zhixu.ai;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * pgvector 持久化向量库封装：
 * - 启动时自动建表 + 扩展（需要 pgvector/pgvector 镜像）；
 * - 上传文档时把带 embedding 的 chunk 写入 pgvector，metadata 中记录归属；
 * - 查询时用 cosine 相似度取 topK，并把「归属过滤」下推到 SQL，
 *   避免其它课程/其它用户的 chunk 挤占召回名额；
 * - 删除文档时按 docId 清除。
 * 不可用时自动降级回本地 ChunkIndex（关键词检索）。
 */
public class PgVectorStore {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStore.class);

    private final PgVectorEmbeddingStore store;
    private final String table;
    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final boolean available;

    public PgVectorStore(AiProperties p) {
        this.table = p.getPgTable();
        this.user = p.getPgUser();
        this.password = p.getPgPassword();
        String jdbcUrl = "jdbc:postgresql://" + p.getPgHost() + ":" + p.getPgPort() + "/" + p.getPgDatabase();
        this.jdbcUrl = jdbcUrl;
        boolean ok;
        PgVectorEmbeddingStore s = null;
        try {
            s = PgVectorEmbeddingStore.builder()
                    .host(p.getPgHost())
                    .port(p.getPgPort())
                    .database(p.getPgDatabase())
                    .user(p.getPgUser())
                    .password(p.getPgPassword())
                    .table(p.getPgTable())
                    .dimension(p.getEmbeddingDimension())
                    .build();
            ok = true;
        } catch (Throwable e) {
            log.warn("pgvector 初始化失败，降级为本地索引: {}", e.getMessage());
            ok = false;
        }
        this.store = s;
        this.available = ok;
    }

    public boolean isAvailable() { return available; }

    /** 写入一批 chunk（含 embedding）。 */
    public void addAll(List<KnowledgeChunk> chunks) {
        if (!available || chunks == null || chunks.isEmpty()) return;
        List<TextSegment> segments = new ArrayList<>();
        List<Embedding> embeddings = new ArrayList<>();
        for (KnowledgeChunk c : chunks) {
            if (c.getEmbedding() == null || c.getEmbedding().length == 0) continue;
            Metadata md = new Metadata();
            md.put("chunkId", c.getChunkId());
            md.put("docId", c.getDocId());
            // ownerType / ownerId 用于向量检索时下推过滤；userId 保留兼容旧数据
            md.put("ownerType", c.getOwnerType() == null ? "" : c.getOwnerType());
            md.put("ownerId", c.getUserId() == null ? "" : String.valueOf(c.getUserId()));
            md.put("userId", c.getUserId() == null ? "" : String.valueOf(c.getUserId()));
            md.put("docName", c.getDocName() == null ? "" : c.getDocName());
            segments.add(TextSegment.from(c.getText(), md));
            embeddings.add(new Embedding(c.getEmbedding()));
        }
        if (segments.isEmpty()) return;
        try {
            store.addAll(embeddings, segments);
        } catch (Throwable e) {
            log.warn("pgvector 写入失败: {}", e.getMessage());
        }
    }

    /**
     * 按向量取 topK，返回 chunkId 与 score。
     * 归属过滤下推到 SQL：只召回 ownerType + ownerId 匹配的 chunk，
     * 避免其它归属的数据占满 topK 名额（否则课程库一多，召回率会明显下降）。
     */
    public List<ScoredId> search(float[] queryEmbedding, int topK) {
        return search(queryEmbedding, topK, null, null);
    }

    public List<ScoredId> search(float[] queryEmbedding, int topK, String ownerType, Long ownerId) {
        if (!available || queryEmbedding == null || queryEmbedding.length == 0) return Collections.emptyList();
        try {
            if (ownerType != null && ownerId != null) {
                List<ScoredId> filtered = searchWithOwnerFilter(queryEmbedding, topK, ownerType, ownerId);
                if (filtered != null) return filtered;
            }
            EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                    .queryEmbedding(new Embedding(queryEmbedding))
                    .maxResults(topK)
                    .build();
            List<EmbeddingMatch<TextSegment>> matches = store.search(req).matches();
            List<ScoredId> out = new ArrayList<>(matches.size());
            for (EmbeddingMatch<TextSegment> m : matches) {
                String chunkId = m.embedded().metadata().getString("chunkId");
                if (chunkId != null) out.add(new ScoredId(chunkId, m.score()));
            }
            return out;
        } catch (Throwable e) {
            log.warn("pgvector 检索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 带归属过滤的向量检索：直接查 embedding 表并用 metadata 过滤，
     * 让数据库在过滤后的子集里做排序，返回 null 表示降级到通用检索。
     *
     * 兼容历史数据：早期写入的 chunk 没有 ownerType/ownerId，只写了 userId，
     * 因此对「ownerType 为空」的记录按 userId 兜底匹配。
     */
    private List<ScoredId> searchWithOwnerFilter(float[] queryEmbedding, int topK, String ownerType, Long ownerId) {
        // pgvector 的 <=> 为 cosine 距离；此处换算为相似度
        String sql = "SELECT metadata->>'chunkId' AS chunk_id, 1 - (embedding <=> ?::vector) AS score " +
                "FROM " + table + " " +
                "WHERE (metadata->>'ownerType' = ? AND metadata->>'ownerId' = ?) " +
                "   OR (COALESCE(metadata->>'ownerType','') = '' AND metadata->>'userId' = ?) " +
                "ORDER BY embedding <=> ?::vector LIMIT ?";
        String vectorLiteral = toVectorLiteral(queryEmbedding);
        List<ScoredId> out = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vectorLiteral);
            ps.setString(2, ownerType.toUpperCase(java.util.Locale.ROOT));
            ps.setString(3, String.valueOf(ownerId));
            ps.setString(4, String.valueOf(ownerId));
            ps.setString(5, vectorLiteral);
            ps.setInt(6, Math.max(topK, 1));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String chunkId = rs.getString("chunk_id");
                    if (chunkId != null) out.add(new ScoredId(chunkId, rs.getDouble("score")));
                }
            }
            return out;
        } catch (Throwable e) {
            log.debug("pgvector 归属过滤检索不可用，降级为通用检索: {}", e.getMessage());
            return null;
        }
    }

    private static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    /** 删除整个文档的所有 chunk。 */
    public void removeByDoc(String docId) {
        if (!available) return;
        String sql = "DELETE FROM " + table + " WHERE metadata->>'docId' = ?";
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, docId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("pgvector 删除失败: {}", e.getMessage());
        }
    }

    public static final class ScoredId {
        public final String chunkId;
        public final double score;
        public ScoredId(String chunkId, double score) { this.chunkId = chunkId; this.score = score; }
    }
}
