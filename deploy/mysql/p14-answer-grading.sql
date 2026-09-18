-- =============================================================================
-- P14 · 作答与判分链路 · 迁移脚本
-- 对应文档：p14-answer-grading-pipeline.md §2
-- 幂等：可重复执行（IF NOT EXISTS / information_schema 判断）
--
-- ⚠️ 本脚本**不修改任何现有数据**（只加列、加表），比 p13 的迁移安全。
--    唯一的"历史影响"是：以前没有作答数据，所以题库的正确率一开始一律是「无数据」（显示 —）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. exam 补 section_id：随堂练习（exam_type=2）挂在哪个小节上
--    正式考试（exam_type=1）仍按 course_id 组织，此列为 NULL
-- -----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exam' AND COLUMN_NAME = 'section_id');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `exam` ADD COLUMN `section_id` BIGINT DEFAULT NULL COMMENT ''随堂练习挂在哪个小节（course_catalogue.id；exam_type=2 时有值）'' AFTER `course_id`',
    'SELECT ''exam.section_id already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exam' AND INDEX_NAME = 'idx_section');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE `exam` ADD INDEX `idx_section` (`section_id`)',
    'SELECT ''exam.idx_section already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2. 作答记录（一场考试 / 一次练习，一个学生一条）
--    uk_exam_student 把「考试只能考一次」变成数据库事实，而不是靠代码自觉
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `exam_record` (
    `id`             BIGINT       NOT NULL COMMENT '主键（雪花）',
    `exam_id`        BIGINT       NOT NULL COMMENT '考试/练习id',
    `student_id`     BIGINT       NOT NULL COMMENT '作答学生（用户id）',
    `score`          INT          NOT NULL DEFAULT 0 COMMENT '得分',
    `total_score`    INT          NOT NULL DEFAULT 0 COMMENT '卷面总分',
    `correct_count`  INT          NOT NULL DEFAULT 0 COMMENT '答对题数',
    `total_count`    INT          NOT NULL DEFAULT 0 COMMENT '总题数',
    `passed`         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否及格（按 exam.pass_score）',
    `start_time`     DATETIME     DEFAULT NULL COMMENT '开始作答时间',
    `finish_time`    DATETIME     DEFAULT NULL COMMENT '交卷时间',
    `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0：进行中（开始但未交卷），1：已交卷，2：已复核',
    `paper_version`  VARCHAR(8)   DEFAULT NULL COMMENT '作答时的试卷版本',
    `snapshot_items` JSON         DEFAULT NULL COMMENT '开始作答瞬间冻结的试卷快照：回看、判分都用它，后续重新发布不影响这一份',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_exam_student` (`exam_id`, `student_id`),
    KEY `idx_student` (`student_id`),
    KEY `idx_exam` (`exam_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考试/练习作答记录';

-- 说明：为什么记录上要再存一份快照（而不是只存 exam_id 去读 exam.snapshot_items）
--   试卷可以被**重新发布**，那时 exam.snapshot_items 会被覆盖 —— 只存引用的话，
--   学生三个月后回看，看到的是改版后的题，而不是他当时做的那份。这与「快照保护」的承诺冲突。
--   所以开始作答时把这一份卷子冻结在记录上。代价是每个学生一份副本；
--   将来量大再改成「按 (exam_id, paper_version) 建版本化快照表」，那时这两列就是迁移来源。

-- -----------------------------------------------------------------------------
-- 3. 作答明细（每题一行）
--    question_id 指向题库题，用于交卷后回写 question.answer_times / correct_times
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `exam_record_detail` (
    `id`          BIGINT      NOT NULL COMMENT '主键（雪花）',
    `record_id`   BIGINT      NOT NULL COMMENT '所属作答记录（exam_record.id）',
    `question_id` BIGINT      NOT NULL COMMENT '题目id（题库题id）',
    `answer`      VARCHAR(64) DEFAULT NULL COMMENT '学生答案（选项编号升序串，如 "1,3"）',
    `correct`     TINYINT     NOT NULL DEFAULT 0 COMMENT '是否正确',
    `score`       INT         NOT NULL DEFAULT 0 COMMENT '本题得分',
    `marked`      TINYINT     NOT NULL DEFAULT 0 COMMENT '学生是否标记待查（答题卡用）',
    PRIMARY KEY (`id`),
    KEY `idx_record` (`record_id`),
    KEY `idx_question` (`question_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作答明细';

-- -----------------------------------------------------------------------------
-- 4. 自检（跑完看一眼，两条都该是 1）
-- -----------------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exam_record')         AS has_exam_record,
    (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exam_record_detail')  AS has_exam_record_detail,
    (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exam' AND COLUMN_NAME = 'section_id') AS has_section_id;
