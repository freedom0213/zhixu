-- =============================================================================
-- P11 教师端建课后端 · 补齐草稿表（tj_course 库）
-- -----------------------------------------------------------------------------
-- 背景：老系统的建课是「草稿 → 上架」双向模型：
--   course_draft / course_catalogue_draft / course_content_draft /
--   course_teacher_draft / course_cata_subject_draft  （老师编辑中的草稿）
--              ↓ upShelf 上架时 copyToShelf
--   course / course_catalogue / course_content / course_teacher / course_cata_subject （学生看的正式数据）
-- 代码里 PO 齐全，但**本环境的库里没有这 5 张草稿表** —— 于是「建课」链路在
-- 草稿阶段就断了（教师端向导保存无处可落）。本脚本补齐它们。
--
-- 字段以 tj-course 的 PO 为准：
--   · draft 表比正式表多 can_update（是否可改）与 c_version（并发版本）
--   · CourseDraft / CourseCatalogueDraft / CourseContentDraft 的 PO 没有 deleted
--   · course_teacher_draft 保留 deleted（PO 有）
--
-- 幂等：全部 CREATE TABLE IF NOT EXISTS，可重复执行。
-- ⚠️ 用命令行执行时加 --default-character-set=utf8mb4，避免中文乱码。
-- =============================================================================

USE tj_course;

-- 1. 课程草稿
CREATE TABLE IF NOT EXISTS `course_draft` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL,
  `course_type` tinyint NOT NULL DEFAULT '2',
  `cover_url` varchar(500) DEFAULT NULL,
  `first_cate_id` bigint DEFAULT NULL,
  `second_cate_id` bigint DEFAULT NULL,
  `third_cate_id` bigint DEFAULT NULL,
  `free` tinyint NOT NULL DEFAULT '0',
  `price` int NOT NULL DEFAULT '0',
  `template_type` tinyint DEFAULT '1',
  `template_url` varchar(500) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `purchase_start_time` datetime DEFAULT NULL,
  `purchase_end_time` datetime DEFAULT NULL,
  `step` tinyint NOT NULL DEFAULT '1',
  `score` int NOT NULL DEFAULT '45',
  `media_duration` int NOT NULL DEFAULT '0',
  `valid_duration` int NOT NULL DEFAULT '12',
  `section_num` int NOT NULL DEFAULT '0',
  `can_update` tinyint NOT NULL DEFAULT '1',
  `c_version` int NOT NULL DEFAULT '0',
  `dep_id` bigint DEFAULT NULL,
  `publish_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creater` bigint DEFAULT NULL,
  `updater` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_course_draft_status` (`status`),
  KEY `idx_course_draft_creater` (`creater`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程草稿（教师端建课编辑中）';

-- 2. 章节 / 小节草稿（type：1=章，2=小节；parent_catalogue_id 指向所属章）
CREATE TABLE IF NOT EXISTS `course_catalogue_draft` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL,
  `trailer` tinyint NOT NULL DEFAULT '0',
  `course_id` bigint NOT NULL,
  `type` tinyint NOT NULL,
  `parent_catalogue_id` bigint NOT NULL DEFAULT '0',
  `media_id` bigint DEFAULT NULL,
  `video_id` bigint DEFAULT NULL,
  `video_name` varchar(255) DEFAULT NULL,
  `living_start_time` datetime DEFAULT NULL,
  `living_end_time` datetime DEFAULT NULL,
  `play_back` tinyint NOT NULL DEFAULT '0',
  `media_duration` int NOT NULL DEFAULT '0',
  `c_index` int NOT NULL DEFAULT '1',
  `can_update` tinyint NOT NULL DEFAULT '1',
  `dep_id` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creater` bigint DEFAULT NULL,
  `updater` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cata_draft_course` (`course_id`,`type`,`c_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程目录草稿（章/小节）';

-- 3. 课程内容草稿（简介 / 适用人群 / 详情）
CREATE TABLE IF NOT EXISTS `course_content_draft` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_introduce` text,
  `use_people` varchar(500) DEFAULT NULL,
  `course_detail` longtext,
  `dep_id` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creater` bigint DEFAULT NULL,
  `updater` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程内容草稿';

-- 4. 课程讲师草稿
CREATE TABLE IF NOT EXISTS `course_teacher_draft` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `teacher_id` bigint NOT NULL,
  `is_show` tinyint NOT NULL DEFAULT '1',
  `c_index` int NOT NULL DEFAULT '1',
  `dep_id` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creater` bigint DEFAULT NULL,
  `updater` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_course_teacher_draft` (`course_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程讲师草稿';

-- 5. 小节配题草稿（小节 ↔ 题目引用；题目本体在 tj_exam）
CREATE TABLE IF NOT EXISTS `course_cata_subject_draft` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `cata_id` bigint NOT NULL,
  `subject_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cata_subject_draft` (`cata_id`),
  KEY `idx_cata_subject_draft_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小节配题草稿（引用题库题目）';

-- -----------------------------------------------------------------------------
-- 6. 主键自增修正（幂等）
-- -----------------------------------------------------------------------------
-- MyBatis-Plus 全局 id-type=auto（见 deploy/nacos/configs/shared-mybatis.yaml），
-- 即主键由数据库自增发号。首次建表若漏了 AUTO_INCREMENT，插入会报
-- 「Field 'id' doesn't have a default value」。这里补一次，老库也能修好。
-- （MySQL 无 ADD AUTO_INCREMENT IF NOT EXISTS，用动态 SQL 判断当前 EXTRA 再改）
SET @tbl := NULL;
SET @sql := NULL;

DROP PROCEDURE IF EXISTS fix_course_draft_ai;
DELIMITER $$
CREATE PROCEDURE fix_course_draft_ai()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE tname VARCHAR(64);
  DECLARE cur CURSOR FOR
    SELECT table_name FROM information_schema.columns
    WHERE table_schema = DATABASE() AND column_name = 'id'
      AND table_name IN ('course_draft','course_catalogue_draft','course_content_draft','course_cata_subject_draft')
      AND extra NOT LIKE '%auto_increment%';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO tname;
    IF done = 1 THEN LEAVE read_loop; END IF;
    SET @s := CONCAT('ALTER TABLE `', tname, '` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT');
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;
CALL fix_course_draft_ai();
DROP PROCEDURE IF EXISTS fix_course_draft_ai;
