-- ============================================================
-- 知序学堂 AI 改造：会话/消息持久化表
-- 说明：tj-ai 服务启动时会自动执行 CREATE TABLE IF NOT EXISTS，
--       此脚本用于手动初始化或核对表结构（PostgreSQL，与 pgvector 同库）。
-- ============================================================

-- AI 会话表（按助手类型隔离：GLOBAL=全局助手 / COURSE=课程助教 / PRIVATE=私人助手）
CREATE TABLE IF NOT EXISTS ai_session (
    id             text PRIMARY KEY,                        -- 会话 UUID
    user_id        bigint      NOT NULL,                    -- 所属用户
    name           text        NOT NULL DEFAULT '新会话',    -- 会话名称
    tag            text        NOT NULL DEFAULT '',         -- 会话标签
    assistant_type text        NOT NULL DEFAULT 'PRIVATE',  -- 助手类型
    course_id      bigint,                                  -- COURSE 类型时的课程 ID
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

-- AI 消息表（一轮对话 = USER + AI 两条记录）
CREATE TABLE IF NOT EXISTS ai_message (
    id            bigserial PRIMARY KEY,
    session_id    text        NOT NULL,                    -- 关联 ai_session.id
    role          text        NOT NULL,                    -- USER / AI
    content       text        NOT NULL,                    -- JSON 字符串（与前端兼容）
    segment_index bigint      NOT NULL,                    -- 会话内消息序号
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_message_session ON ai_message(session_id, segment_index);
CREATE INDEX IF NOT EXISTS idx_ai_session_user    ON ai_session(user_id, assistant_type);
