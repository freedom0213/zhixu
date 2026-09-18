-- =============================================================================
-- P11 教师端后端 · 阶段 1/2 迁移脚本（题库缺口 + 考试实体）
-- 对应契约：p11-teacher-api-contract.md §4 / §9
-- 幂等：可重复执行（IF NOT EXISTS / IF EXISTS 判断）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. question 表补 status 列（1 可用 / 0 已停用；停用不删除，见契约 §4.3）
-- -----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND COLUMN_NAME = 'status');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `question` ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT ''状态，1：可用，0：已停用'' AFTER `difficulty`',
    'SELECT ''question.status already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2. 知识点表（课程级；录题时只能选不能新建，见契约 §4）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_point` (
    `id`          BIGINT       NOT NULL COMMENT '主键（雪花）',
    `course_id`   BIGINT       NOT NULL COMMENT '所属课程id（course 库的课程；此处只存引用）',
    `name`        VARCHAR(64)  NOT NULL COMMENT '知识点名称',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_name` (`course_id`, `name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '课程知识点';

-- -----------------------------------------------------------------------------
-- 3. 考试实体（此前不存在；契约 §9 的 7 个接口依赖此表）
--    items          = 草稿阶段的「引用」：[{questionId, score}]
--    snapshot_items = 发布瞬间冻结的快照（完整题目内容副本），发布前为 NULL
--    统计字段 submitted_count 等由批改/统计阶段回填，本轮恒 0 / NULL
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `exam` (
    `id`              BIGINT       NOT NULL COMMENT '主键（雪花）',
    `name`            VARCHAR(128) NOT NULL COMMENT '考试名称',
    `course_id`       BIGINT       NOT NULL COMMENT '关联课程id',
    `course_name`     VARCHAR(128) DEFAULT NULL COMMENT '课程名称（冗余，避免跨库 join）',
    `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '状态，0：草稿，1：已发布，2：批改中，3：已结束',
    `exam_type`       TINYINT      NOT NULL DEFAULT 1 COMMENT '类型，1：正式考试，2：随堂练习',
    `pass_score`      INT          NOT NULL DEFAULT 60 COMMENT '及格线',
    `duration`        INT          NOT NULL DEFAULT 90 COMMENT '考试时长（分钟）',
    `notice`          VARCHAR(512) DEFAULT NULL COMMENT '考试须知',
    `start_at`        DATETIME     DEFAULT NULL COMMENT '开始时间',
    `end_at`          DATETIME     DEFAULT NULL COMMENT '结束时间',
    `items`           JSON         NOT NULL COMMENT '引用：[{questionId, score}]',
    `snapshot_items`  JSON         DEFAULT NULL COMMENT '快照：发布时冻结的完整题目内容，发布前为 NULL',
    `paper_version`   VARCHAR(8)   DEFAULT NULL COMMENT '试卷版本，发布后为 v1',
    `snapshot_at`     DATETIME     DEFAULT NULL COMMENT '冻结时间',
    `submitted_count` INT          NOT NULL DEFAULT 0 COMMENT '已提交人数（批改阶段回填）',
    `creater`         BIGINT       NOT NULL COMMENT '创建人',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_creater` (`creater`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考试';

-- -----------------------------------------------------------------------------
-- 4. 种子知识点（与前端 mock 的 302 课一致，方便联调）
-- ⚠️ 用命令行执行本文件时必须加 --default-character-set=utf8mb4，否则中文种子会乱码
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `knowledge_point` (`id`, `course_id`, `name`) VALUES
    (1, 302, '自动配置'),
    (2, 302, '条件注解'),
    (3, 302, '起步依赖');
