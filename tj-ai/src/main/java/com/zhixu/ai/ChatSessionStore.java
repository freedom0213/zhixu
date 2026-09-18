package com.zhixu.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话/消息持久化存储。
 * 优先写入 PostgreSQL（与 pgvector 同库，纯 JDBC，无额外依赖）；
 * 数据库不可用时自动降级为内存存储（仅本地调试用，重启即失）。
 *
 * 表结构（启动时自动创建）：
 *   ai_session(id text PK, user_id bigint, name text, tag text,
 *              assistant_type text, course_id bigint, created_at, updated_at)
 *   ai_message(id bigserial PK, session_id text, role text, content text,
 *              segment_index bigint, created_at)
 */
public class ChatSessionStore {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionStore.class);

    /** 助手类型常量。 */
    public static final String TYPE_GLOBAL = "GLOBAL";
    public static final String TYPE_COURSE = "COURSE";
    public static final String TYPE_PRIVATE = "PRIVATE";

    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;
    private final boolean dbAvailable;

    /** 内存降级存储（dbAvailable=false 时使用）。 */
    private final Map<String, Map<String, Object>> memSessions = new ConcurrentHashMap<>();
    private final AtomicLong memSeq = new AtomicLong();

    public ChatSessionStore(AiProperties p) {
        this.jdbcUrl = "jdbc:postgresql://" + p.getPgHost() + ":" + p.getPgPort() + "/" + p.getPgDatabase();
        this.dbUser = p.getPgUser();
        this.dbPassword = p.getPgPassword();
        this.dbAvailable = initTables();
        if (dbAvailable) {
            log.info("AI 会话持久化已启用（PostgreSQL）");
        } else {
            log.warn("AI 会话持久化不可用，降级为内存存储（重启丢失历史）");
        }
    }

    public boolean isDbAvailable() { return dbAvailable; }

    private boolean initTables() {
        String ddlSession = "CREATE TABLE IF NOT EXISTS ai_session (" +
                "id text PRIMARY KEY," +
                "user_id bigint NOT NULL," +
                "name text NOT NULL DEFAULT '新会话'," +
                "tag text NOT NULL DEFAULT ''," +
                "assistant_type text NOT NULL DEFAULT 'PRIVATE'," +
                "course_id bigint," +
                "created_at timestamptz NOT NULL DEFAULT now()," +
                "updated_at timestamptz NOT NULL DEFAULT now())";
        String ddlMessage = "CREATE TABLE IF NOT EXISTS ai_message (" +
                "id bigserial PRIMARY KEY," +
                "session_id text NOT NULL," +
                "role text NOT NULL," +
                "content text NOT NULL," +
                "segment_index bigint NOT NULL," +
                "created_at timestamptz NOT NULL DEFAULT now())";
        String idx = "CREATE INDEX IF NOT EXISTS idx_ai_message_session ON ai_message(session_id, segment_index)";
        String idx2 = "CREATE INDEX IF NOT EXISTS idx_ai_session_user ON ai_session(user_id, assistant_type)";
        try (Connection conn = conn(); Statement st = conn.createStatement()) {
            st.execute(ddlSession);
            st.execute(ddlMessage);
            st.execute(idx);
            st.execute(idx2);
            return true;
        } catch (Throwable e) {
            log.warn("ai_session/ai_message 建表失败: {}", e.getMessage());
            return false;
        }
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
    }

    // ==================== 会话 ====================

    /** 查询用户某类助手的会话列表（按更新时间倒序）。 */
    public List<Map<String, Object>> listSessions(Long userId, String assistantType) {
        if (userId == null) return Collections.emptyList();
        if (!dbAvailable) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> s : memSessions.values()) {
                if (Objects.equals(s.get("userId"), userId)
                        && (assistantType == null || assistantType.equals(s.get("assistantType")))) {
                    out.add(new LinkedHashMap<>(s));
                }
            }
            out.sort((a, b) -> Long.compare((long) b.getOrDefault("updatedAt", 0L), (long) a.getOrDefault("updatedAt", 0L)));
            return out;
        }
        String sql = "SELECT id, name, tag, assistant_type, course_id, " +
                "EXTRACT(EPOCH FROM updated_at)::bigint AS updated_at " +
                "FROM ai_session WHERE user_id = ? AND assistant_type = ? ORDER BY updated_at DESC";
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection conn = conn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, assistantType == null ? TYPE_PRIVATE : assistantType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("sessionId", rs.getString("id"));
                    s.put("id", rs.getString("id"));
                    s.put("name", rs.getString("name"));
                    s.put("tag", rs.getString("tag"));
                    s.put("assistantType", rs.getString("assistant_type"));
                    long courseId = rs.getLong("course_id");
                    s.put("courseId", rs.wasNull() ? null : courseId);
                    s.put("updatedAt", rs.getLong("updated_at"));
                    out.add(s);
                }
            }
        } catch (Throwable e) {
            log.warn("查询会话列表失败: {}", e.getMessage());
        }
        return out;
    }

    /** 创建会话；type 为 GLOBAL/COURSE/PRIVATE。 */
    public Map<String, Object> createSession(Long userId, String name, String tag, String assistantType, Long courseId) {
        String id = UUID.randomUUID().toString();
        String type = assistantType == null || assistantType.isBlank() ? TYPE_PRIVATE : assistantType;
        if (!dbAvailable) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", id); s.put("sessionId", id);
            s.put("userId", userId);
            s.put("name", name); s.put("tag", tag);
            s.put("assistantType", type);
            s.put("courseId", courseId);
            s.put("updatedAt", System.currentTimeMillis() / 1000);
            memSessions.put(id, s);
            return new LinkedHashMap<>(s);
        }
        String sql = "INSERT INTO ai_session(id, user_id, name, tag, assistant_type, course_id) VALUES (?,?,?,?,?,?)";
        try (Connection conn = conn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setLong(2, userId);
            ps.setString(3, name);
            ps.setString(4, tag == null ? "" : tag);
            ps.setString(5, type);
            if (courseId == null) ps.setNull(6, Types.BIGINT); else ps.setLong(6, courseId);
            ps.executeUpdate();
        } catch (Throwable e) {
            log.warn("创建会话失败: {}", e.getMessage());
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", id); s.put("sessionId", id);
        s.put("userId", userId);
        s.put("name", name); s.put("tag", tag);
        s.put("assistantType", type);
        s.put("courseId", courseId);
        return s;
    }

    /** 校验会话归属 + 类型；返回会话信息，不合法返回 null。 */
    public Map<String, Object> ownSession(Long userId, String sessionId, String assistantType) {
        if (userId == null || sessionId == null || sessionId.isBlank()) return null;
        String type = assistantType == null ? TYPE_PRIVATE : assistantType;
        if (!dbAvailable) {
            Map<String, Object> s = memSessions.get(sessionId);
            if (s == null) return null;
            return Objects.equals(s.get("userId"), userId) && type.equals(s.get("assistantType")) ? s : null;
        }
        String sql = "SELECT id, name, tag, assistant_type, course_id FROM ai_session WHERE id = ? AND user_id = ? AND assistant_type = ?";
        try (Connection conn = conn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setLong(2, userId);
            ps.setString(3, type);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("id", rs.getString("id"));
                    s.put("sessionId", rs.getString("id"));
                    s.put("userId", userId);
                    s.put("name", rs.getString("name"));
                    s.put("tag", rs.getString("tag"));
                    s.put("assistantType", rs.getString("assistant_type"));
                    long courseId = rs.getLong("course_id");
                    s.put("courseId", rs.wasNull() ? null : courseId);
                    return s;
                }
            }
        } catch (Throwable e) {
            log.warn("校验会话失败: {}", e.getMessage());
        }
        return null;
    }

    /** 更新会话名称/标签；成功返回 true。 */
    public boolean updateSession(Long userId, String sessionId, String name, String tag, String assistantType) {
        Map<String, Object> s = ownSession(userId, sessionId, assistantType);
        if (s == null) return false;
        if (!dbAvailable) {
            s.put("name", name); s.put("tag", tag);
            return true;
        }
        String sql = "UPDATE ai_session SET name = ?, tag = ?, updated_at = now() WHERE id = ?";
        try (Connection conn = conn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, tag == null ? "" : tag);
            ps.setString(3, sessionId);
            return ps.executeUpdate() > 0;
        } catch (Throwable e) {
            log.warn("更新会话失败: {}", e.getMessage());
            return false;
        }
    }

    /** 删除会话及其全部消息。 */
    public void deleteSession(Long userId, String sessionId, String assistantType) {
        if (ownSession(userId, sessionId, assistantType) == null) return;
        if (!dbAvailable) {
            memSessions.remove(sessionId);
            return;
        }
        try (Connection conn = conn()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ai_message WHERE session_id = ?")) {
                ps.setString(1, sessionId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ai_session WHERE id = ?")) {
                ps.setString(1, sessionId);
                ps.executeUpdate();
            }
        } catch (Throwable e) {
            log.warn("删除会话失败: {}", e.getMessage());
        }
    }

    // ==================== 消息 ====================

    /** 追加一轮对话（USER + AI 两条消息）。 */
    public void appendRound(Long userId, String sessionId, String assistantType, String question, String answer) {
        Map<String, Object> s = ownSession(userId, sessionId, assistantType);
        if (s == null) return;
        long base = nextSegment(sessionId);
        if (!dbAvailable) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = (List<Map<String, Object>>) s.computeIfAbsent("records",
                    k -> Collections.synchronizedList(new ArrayList<>()));
            synchronized (records) {
                records.add(messageRecord("USER", question, base));
                records.add(messageRecord("AI", answer, base + 1));
            }
            s.put("updatedAt", System.currentTimeMillis() / 1000);
            return;
        }
        String sql = "INSERT INTO ai_message(session_id, role, content, segment_index) VALUES (?,?,?,?)";
        try (Connection conn = conn()) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, sessionId); ps.setString(2, "USER");
                ps.setString(3, question == null ? "" : question);
                ps.setLong(4, base);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, sessionId); ps.setString(2, "AI");
                ps.setString(3, answer == null ? "" : answer);
                ps.setLong(4, base + 1);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE ai_session SET updated_at = now() WHERE id = ?")) {
                ps.setString(1, sessionId);
                ps.executeUpdate();
            }
        } catch (Throwable e) {
            log.warn("写入消息失败: {}", e.getMessage());
        }
    }

    /** 查询会话消息（按 segment_index 正序）。前端拿到的顺序即对话顺序。 */
    public List<Map<String, Object>> listRecords(Long userId, String sessionId, String assistantType) {
        if (ownSession(userId, sessionId, assistantType) == null) return Collections.emptyList();
        if (!dbAvailable) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = (List<Map<String, Object>>) ownSession(userId, sessionId, assistantType).get("records");
            if (records == null) return Collections.emptyList();
            synchronized (records) { return new ArrayList<>(records); }
        }
        String sql = "SELECT role, content, segment_index FROM ai_message WHERE session_id = ? ORDER BY segment_index ASC";
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection conn = conn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(messageRecord(rs.getString("role"), rs.getString("content"), rs.getLong("segment_index")));
                }
            }
        } catch (Throwable e) {
            log.warn("查询消息失败: {}", e.getMessage());
        }
        return out;
    }

    private long nextSegment(String sessionId) {
        if (!dbAvailable) return memSeq.incrementAndGet() * 2;
        String sql = "SELECT COALESCE(MAX(segment_index), 0) FROM ai_message WHERE session_id = ?";
        try (Connection conn = conn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1) + 1;
            }
        } catch (Throwable ignored) { }
        return 1;
    }

    /** 与前端 ai.vue 兼容的消息结构：content 为 JSON 字符串。 */
    private Map<String, Object> messageRecord(String type, String text, long segmentIndex) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", type);
        if ("USER".equals(type)) {
            content.put("contents", List.of(Map.of("text", text == null ? "" : text)));
        } else {
            content.put("text", text == null ? "" : text);
        }
        String json;
        try {
            json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(content);
        } catch (Exception e) {
            json = "{\"type\":\"" + type + "\"}";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", json);
        out.put("segmentIndex", segmentIndex);
        return out;
    }
}
