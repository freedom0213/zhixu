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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * pgvector 持久化向量库封装：
 * - 启动时自动建表 + 扩展（需要 pgvector/pgvector 镜像）；
 * - 上传文档时把带 embedding 的 chunk 写入 pgvector；
 * - 查询时用 cosine 相似度取 topK；
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

    /** 按向量取 topK，返回 chunkId 与 score；内容由调用方从本地索引取。 */
    public List<ScoredId> search(float[] queryEmbedding, int topK) {
        if (!available || queryEmbedding == null || queryEmbedding.length == 0) return Collections.emptyList();
        try {
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
