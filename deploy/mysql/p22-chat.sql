-- =============================================================================
-- P22 · 师生对话（私信 + WebSocket）
-- -----------------------------------------------------------------------------
-- 两张表，塞在 message 库（tj_message）：
--   chat_conversation  会话 = 两个人（user_low/user_high 规范成对，保证两人只有一条会话）
--   chat_message       消息（不物理删；撤回/删除是后续话题，先不做）
--
-- 未读数直接放在会话行上（unread_low / unread_high 各记各的）：
--   · 发消息 → 接收方那一侧 +1
--   · 接收方打开会话拉历史 → 自己那一侧清 0
--   好处是「会话列表 + 未读角标」一次查询就够，不用对消息表做 COUNT。
-- =============================================================================

-- ① 会话表
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'CREATE TABLE `chat_conversation` (\n\
  `id` BIGINT NOT NULL COMMENT ''会话id'',\n\
  `user_low` BIGINT NOT NULL COMMENT ''参与者中较小的用户id'',\n\
  `user_high` BIGINT NOT NULL COMMENT ''参与者中较大的用户id'',\n\
  `last_message` VARCHAR(255) NULL COMMENT ''最后一条消息摘要（列表预览）'',\n\
  `last_time` DATETIME NULL COMMENT ''最后一条消息时间'',\n\
  `unread_low` INT NOT NULL DEFAULT 0 COMMENT ''user_low 的未读数'',\n\
  `unread_high` INT NOT NULL DEFAULT 0 COMMENT ''user_high 的未读数'',\n\
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,\n\
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n\
  PRIMARY KEY (`id`),\n\
  UNIQUE KEY `uk_pair` (`user_low`, `user_high`),\n\
  KEY `idx_low_time` (`user_low`, `last_time`),\n\
  KEY `idx_high_time` (`user_high`, `last_time`)\n\
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT=''师生对话·会话''',
    'SELECT ''chat_conversation 已存在，跳过'' AS note')
FROM information_schema.tables
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_conversation');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ② 消息表
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'CREATE TABLE `chat_message` (\n\
  `id` BIGINT NOT NULL COMMENT ''消息id'',\n\
  `conversation_id` BIGINT NOT NULL COMMENT ''会话id'',\n\
  `sender_id` BIGINT NOT NULL COMMENT ''发送者用户id'',\n\
  `content` VARCHAR(1000) NOT NULL COMMENT ''文本内容（图片/文件后续再说）'',\n\
  `push_time` DATETIME NOT NULL COMMENT ''发送时间'',\n\
  PRIMARY KEY (`id`),\n\
  KEY `idx_conv` (`conversation_id`, `id`)\n\
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT=''师生对话·消息''',
    'SELECT ''chat_message 已存在，跳过'' AS note')
FROM information_schema.tables
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_message');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
