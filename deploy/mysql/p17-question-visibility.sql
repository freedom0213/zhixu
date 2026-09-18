-- =============================================================================
-- p17 · 题目「可见范围」（公开 / 仅我）
-- -----------------------------------------------------------------------------
-- 幂等：可重复执行（information_schema 判断列/索引是否存在）
--
-- 背景（用户 2026-09-16 决策）：取消「随堂练习」（小节配题）只保留试卷；
--   同时给题目加「可见范围」——老师可以把自己出的题**公开发布到平台**
--   （引用式共享：只改范围字段，不复制内容，避免出现两份近似重复题）。
--
-- ⚠️ 现有题目一律置为**公开**：否则迁移后全站讲师打开题库会发现"昨天的题不见了"。
--    「可见集合只增不减」是这次迁移的硬要求。
--    只在本轮**刚加上列**时执行存量更新（@col_exists=0 是常量条件），
--    这样重复执行不会把别人后来新设的私有题改回公开。
-- =============================================================================
USE `tj_exam`;

-- ① visibility：0 私有（仅作者可见），1 公开（平台可见）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND COLUMN_NAME = 'visibility');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `question` ADD COLUMN `visibility` TINYINT NOT NULL DEFAULT 0 COMMENT ''可见范围：0 私有（仅作者），1 公开（平台可见）'' AFTER `status`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ② publish_time：公开发布时间（撤回时清空；用于"新公开的题排前面"）
SET @col_exists2 = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND COLUMN_NAME = 'publish_time');
SET @sql2 = IF(@col_exists2 = 0,
    'ALTER TABLE `question` ADD COLUMN `publish_time` DATETIME DEFAULT NULL COMMENT ''公开发布到平台的时间（撤回时清空）'' AFTER `visibility`',
    'SELECT 1');
PREPARE stmt FROM @sql2; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ③ 存量题一律公开（仅本轮刚加列时执行一次）
UPDATE `question`
SET `visibility` = 1,
    `publish_time` = IFNULL(`update_time`, NOW())
WHERE @col_exists = 0;

-- ④ 可见性筛选要用的索引
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND INDEX_NAME = 'idx_visibility');
SET @sql3 = IF(@idx_exists = 0,
    'ALTER TABLE `question` ADD INDEX `idx_visibility` (`visibility`)',
    'SELECT 1');
PREPARE stmt FROM @sql3; EXECUTE stmt; DEALLOCATE PREPARE stmt;
