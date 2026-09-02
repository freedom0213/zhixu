-- Local MVP schema and demo data for the course browsing and order flow.
-- This migration is safe to re-run against an existing local data volume.

SET NAMES utf8mb4;

USE tj_course;

CREATE TABLE IF NOT EXISTS `category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `parent_id` BIGINT NOT NULL DEFAULT 0,
  `level` TINYINT NOT NULL DEFAULT 1,
  `priority` INT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creater` BIGINT DEFAULT NULL,
  `updater` BIGINT DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_category_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `course` (
  `id` BIGINT NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `course_type` TINYINT NOT NULL DEFAULT 2,
  `cover_url` VARCHAR(500) DEFAULT NULL,
  `first_cate_id` BIGINT DEFAULT NULL,
  `second_cate_id` BIGINT DEFAULT NULL,
  `third_cate_id` BIGINT DEFAULT NULL,
  `free` TINYINT NOT NULL DEFAULT 0,
  `price` INT NOT NULL DEFAULT 0,
  `template_type` TINYINT DEFAULT 1,
  `template_url` VARCHAR(500) DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `purchase_start_time` DATETIME DEFAULT NULL,
  `purchase_end_time` DATETIME DEFAULT NULL,
  `step` TINYINT NOT NULL DEFAULT 5,
  `score` INT NOT NULL DEFAULT 45,
  `media_duration` INT NOT NULL DEFAULT 0,
  `valid_duration` INT NOT NULL DEFAULT 12,
  `section_num` INT NOT NULL DEFAULT 0,
  `dep_id` BIGINT DEFAULT NULL,
  `publish_times` INT NOT NULL DEFAULT 1,
  `publish_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creater` BIGINT DEFAULT NULL,
  `updater` BIGINT DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_course_status` (`status`, `deleted`),
  KEY `idx_course_category` (`third_cate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `course_content` (
  `id` BIGINT NOT NULL,
  `course_introduce` TEXT,
  `use_people` VARCHAR(500) DEFAULT NULL,
  `course_detail` LONGTEXT,
  `dep_id` BIGINT DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creater` BIGINT DEFAULT NULL,
  `updater` BIGINT DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `course_catalogue` (
  `id` BIGINT NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `trailer` TINYINT NOT NULL DEFAULT 0,
  `course_id` BIGINT NOT NULL,
  `type` TINYINT NOT NULL,
  `parent_catalogue_id` BIGINT NOT NULL DEFAULT 0,
  `media_id` BIGINT DEFAULT NULL,
  `video_id` BIGINT DEFAULT NULL,
  `video_name` VARCHAR(255) DEFAULT NULL,
  `living_start_time` DATETIME DEFAULT NULL,
  `living_end_time` DATETIME DEFAULT NULL,
  `play_back` TINYINT NOT NULL DEFAULT 0,
  `media_duration` INT NOT NULL DEFAULT 0,
  `c_index` INT NOT NULL DEFAULT 1,
  `dep_id` BIGINT DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creater` BIGINT DEFAULT NULL,
  `updater` BIGINT DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_catalogue_course` (`course_id`, `type`, `c_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `course_teacher` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `course_id` BIGINT NOT NULL,
  `teacher_id` BIGINT NOT NULL,
  `is_show` TINYINT NOT NULL DEFAULT 1,
  `c_index` INT NOT NULL DEFAULT 1,
  `dep_id` BIGINT DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creater` BIGINT DEFAULT NULL,
  `updater` BIGINT DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_teacher` (`course_id`, `teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `course_cata_subject` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `course_id` BIGINT NOT NULL,
  `cata_id` BIGINT NOT NULL,
  `subject_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_cata_subject` (`cata_id`, `subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `course_subject` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `course_id` BIGINT NOT NULL,
  `subject_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `subject` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(500) NOT NULL,
  `subject_type` TINYINT NOT NULL DEFAULT 1,
  `difficulty` TINYINT NOT NULL DEFAULT 1,
  `option1` VARCHAR(500) DEFAULT NULL,
  `option2` VARCHAR(500) DEFAULT NULL,
  `option3` VARCHAR(500) DEFAULT NULL,
  `option4` VARCHAR(500) DEFAULT NULL,
  `option5` VARCHAR(500) DEFAULT NULL,
  `option6` VARCHAR(500) DEFAULT NULL,
  `option7` VARCHAR(500) DEFAULT NULL,
  `option8` VARCHAR(500) DEFAULT NULL,
  `option9` VARCHAR(500) DEFAULT NULL,
  `option10` VARCHAR(500) DEFAULT NULL,
  `answer` VARCHAR(100) DEFAULT NULL,
  `analysis` TEXT,
  `correct_times` INT NOT NULL DEFAULT 0,
  `score` INT NOT NULL DEFAULT 5,
  `dep_id` BIGINT DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `use_times` INT NOT NULL DEFAULT 0,
  `answer_times` INT NOT NULL DEFAULT 0,
  `creater` BIGINT DEFAULT NULL,
  `updater` BIGINT DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `subject_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `subject_id` BIGINT NOT NULL,
  `first_cate_id` BIGINT DEFAULT NULL,
  `second_cate_id` BIGINT DEFAULT NULL,
  `third_cate_id` BIGINT DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `category` (`id`, `name`, `parent_id`, `level`, `priority`, `status`, `creater`, `updater`)
VALUES
  (1, '后端开发', 0, 1, 1, 1, 1, 1),
  (2, 'Java', 1, 2, 1, 1, 1, 1),
  (3, '微服务', 2, 3, 1, 1, 1, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `status` = VALUES(`status`), `deleted` = 0;

INSERT INTO `course` (`id`, `name`, `course_type`, `cover_url`, `first_cate_id`, `second_cate_id`, `third_cate_id`, `free`, `price`, `template_type`, `status`, `purchase_start_time`, `purchase_end_time`, `step`, `score`, `media_duration`, `valid_duration`, `section_num`, `publish_times`, `publish_time`, `creater`, `updater`)
VALUES
  (1001, 'Java 微服务实战入门', 2, '', 1, 2, 3, 0, 19900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 45, 3600, 12, 2, 1, NOW(), 1, 1),
  (1002, 'Spring Boot 快速入门（免费）', 2, '', 1, 2, 3, 1, 0, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 48, 1800, 6, 1, 1, NOW(), 1, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `status` = VALUES(`status`), `purchase_end_time` = VALUES(`purchase_end_time`), `deleted` = 0;

INSERT INTO `course_content` (`id`, `course_introduce`, `use_people`, `course_detail`, `creater`, `updater`)
VALUES
  (1001, '从零搭建可运行的 Spring Cloud 微服务应用。', 'Java 后端初学者和希望了解微服务工程实践的开发者。', '<p>覆盖网关、服务注册发现、配置中心、数据库和消息队列。</p>', 1, 1),
  (1002, '快速掌握 Spring Boot 项目结构与常用开发方式。', 'Java 入门学习者。', '<p>使用本地演示数据即可体验课程浏览流程。</p>', 1, 1)
ON DUPLICATE KEY UPDATE `course_introduce` = VALUES(`course_introduce`), `use_people` = VALUES(`use_people`), `course_detail` = VALUES(`course_detail`), `deleted` = 0;

INSERT INTO `course_catalogue` (`id`, `name`, `trailer`, `course_id`, `type`, `parent_catalogue_id`, `video_name`, `media_duration`, `c_index`, `creater`, `updater`)
VALUES
  (1101, '第一章 微服务基础', 0, 1001, 1, 0, NULL, 1800, 1, 1, 1),
  (1102, '认识服务注册与发现', 1, 1001, 2, 1101, '认识服务注册与发现', 900, 1, 1, 1),
  (1103, '配置中心与网关', 1, 1001, 2, 1101, '配置中心与网关', 900, 2, 1, 1),
  (1201, '第一章 Spring Boot 基础', 1, 1002, 1, 0, NULL, 900, 1, 1, 1),
  (1202, '创建第一个 Spring Boot 应用', 1, 1002, 2, 1201, '创建第一个 Spring Boot 应用', 900, 1, 1, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `course_id` = VALUES(`course_id`), `deleted` = 0;

INSERT INTO `course_teacher` (`id`, `course_id`, `teacher_id`, `is_show`, `c_index`, `creater`, `updater`)
VALUES (1, 1001, 2, 1, 1, 1, 1), (2, 1002, 2, 1, 1, 1, 1)
ON DUPLICATE KEY UPDATE `is_show` = VALUES(`is_show`), `deleted` = 0;

USE tj_search;

CREATE TABLE IF NOT EXISTS `interests` (
  `id` BIGINT NOT NULL,
  `interests` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

USE tj_exam;

CREATE TABLE IF NOT EXISTS `question` (
  `id` BIGINT NOT NULL,
  `name` VARCHAR(500) NOT NULL,
  `type` TINYINT NOT NULL DEFAULT 1,
  `cate_id1` BIGINT DEFAULT NULL,
  `cate_id2` BIGINT DEFAULT NULL,
  `cate_id3` BIGINT DEFAULT NULL,
  `difficulty` TINYINT NOT NULL DEFAULT 1,
  `correct_times` INT NOT NULL DEFAULT 0,
  `answer_times` INT NOT NULL DEFAULT 0,
  `score` INT NOT NULL DEFAULT 5,
  `dep_id` BIGINT DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creater` BIGINT DEFAULT NULL,
  `updater` BIGINT DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `question_detail` (
  `id` BIGINT NOT NULL,
  `options` JSON DEFAULT NULL,
  `answer` VARCHAR(100) DEFAULT NULL,
  `analysis` TEXT,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `question_biz` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `biz_id` BIGINT NOT NULL,
  `question_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_biz` (`biz_id`, `question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `question` (`id`, `name`, `type`, `cate_id1`, `cate_id2`, `cate_id3`, `difficulty`, `score`, `creater`, `updater`)
VALUES (9001, 'Nacos 在微服务架构中的主要作用是什么？', 1, 1, 2, 3, 1, 5, 1, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `question_detail` (`id`, `options`, `answer`, `analysis`)
VALUES (9001, '["服务注册与发现","图片处理","支付扣款","日志压缩"]', '1', 'Nacos 同时提供服务注册发现和配置管理能力。')
ON DUPLICATE KEY UPDATE `options` = VALUES(`options`), `answer` = VALUES(`answer`);
INSERT INTO `question_biz` (`biz_id`, `question_id`) VALUES (1103, 9001)
ON DUPLICATE KEY UPDATE `question_id` = VALUES(`question_id`);

USE tj_learning;

CREATE TABLE IF NOT EXISTS `learning_lesson` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `course_id` BIGINT NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 0,
  `week_freq` INT DEFAULT NULL,
  `plan_status` TINYINT NOT NULL DEFAULT 0,
  `learned_sections` INT NOT NULL DEFAULT 0,
  `latest_section_id` BIGINT DEFAULT NULL,
  `latest_learn_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expire_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_learning_user_course` (`user_id`, `course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `learning_record` (
  `id` BIGINT NOT NULL,
  `lesson_id` BIGINT NOT NULL,
  `section_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `moment` INT NOT NULL DEFAULT 0,
  `finished` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finish_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_learning_record_user` (`user_id`, `finished`, `finish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `interaction_question` (
  `id` BIGINT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` TEXT,
  `course_id` BIGINT NOT NULL,
  `chapter_id` BIGINT DEFAULT NULL,
  `section_id` BIGINT DEFAULT NULL,
  `user_id` BIGINT NOT NULL,
  `latest_answer_id` BIGINT DEFAULT NULL,
  `answer_times` INT NOT NULL DEFAULT 0,
  `anonymity` TINYINT NOT NULL DEFAULT 0,
  `hidden` TINYINT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `interaction_reply` (
  `id` BIGINT NOT NULL,
  `question_id` BIGINT NOT NULL,
  `answer_id` BIGINT DEFAULT NULL,
  `user_id` BIGINT NOT NULL,
  `content` TEXT NOT NULL,
  `target_user_id` BIGINT DEFAULT NULL,
  `target_reply_id` BIGINT DEFAULT NULL,
  `reply_times` INT NOT NULL DEFAULT 0,
  `liked_times` INT NOT NULL DEFAULT 0,
  `hidden` TINYINT NOT NULL DEFAULT 0,
  `anonymity` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `points_board_season` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `begin_time` DATE NOT NULL,
  `end_time` DATE NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `points_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` TINYINT NOT NULL,
  `points` INT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

USE tj_trade;

CREATE TABLE IF NOT EXISTS `cart` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `course_id` BIGINT NOT NULL,
  `cover_url` VARCHAR(500) DEFAULT NULL,
  `course_name` VARCHAR(200) NOT NULL,
  `price` INT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_user_course` (`user_id`, `course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `order` (
  `id` BIGINT NOT NULL,
  `pay_order_no` BIGINT DEFAULT NULL,
  `user_id` BIGINT NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `message` VARCHAR(255) DEFAULT NULL,
  `total_amount` INT NOT NULL DEFAULT 0,
  `real_amount` INT NOT NULL DEFAULT 0,
  `discount_amount` INT NOT NULL DEFAULT 0,
  `pay_channel` VARCHAR(50) DEFAULT NULL,
  `coupon_ids` JSON DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `pay_time` DATETIME DEFAULT NULL,
  `close_time` DATETIME DEFAULT NULL,
  `finish_time` DATETIME DEFAULT NULL,
  `refund_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creater` BIGINT DEFAULT NULL,
  `updater` BIGINT DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_order_user_status` (`user_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `order_detail` (
  `id` BIGINT NOT NULL,
  `order_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `course_id` BIGINT NOT NULL,
  `price` INT NOT NULL DEFAULT 0,
  `name` VARCHAR(200) NOT NULL,
  `cover_url` VARCHAR(500) DEFAULT NULL,
  `valid_duration` INT DEFAULT NULL,
  `course_expire_time` DATETIME DEFAULT NULL,
  `discount_amount` INT NOT NULL DEFAULT 0,
  `real_pay_amount` INT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 1,
  `refund_status` TINYINT DEFAULT NULL,
  `pay_channel` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creater` BIGINT DEFAULT NULL,
  `updater` BIGINT DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_detail_order` (`order_id`),
  KEY `idx_order_detail_course` (`course_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `refund_apply` (
  `id` BIGINT NOT NULL,
  `order_detail_id` BIGINT NOT NULL,
  `order_id` BIGINT NOT NULL,
  `refund_order_no` BIGINT DEFAULT NULL,
  `user_id` BIGINT NOT NULL,
  `refund_amount` INT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 1,
  `message` VARCHAR(255) DEFAULT NULL,
  `refund_reason` VARCHAR(255) DEFAULT NULL,
  `question_desc` VARCHAR(500) DEFAULT NULL,
  `approver` BIGINT DEFAULT NULL,
  `approve_opinion` VARCHAR(500) DEFAULT NULL,
  `remark` VARCHAR(500) DEFAULT NULL,
  `failed_reason` VARCHAR(500) DEFAULT NULL,
  `refund_channel` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `approve_time` DATETIME DEFAULT NULL,
  `finish_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creater` BIGINT DEFAULT NULL,
  `updater` BIGINT DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_refund_order_detail` (`order_detail_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

USE tj_user;

UPDATE `user_detail`
SET `name` = '本地管理员', `type` = 1, `role_id` = 1
WHERE `id` = 1;

INSERT INTO `user` (`id`, `username`, `cell_phone`, `password`, `status`, `type`)
VALUES (2, 'teacher', '13900000000', '$2b$12$IByF0GzRp4uihfSsp1nRJ.h2IiKtMEZDgg7IvesuqyPy4vJ0DWR5W', 1, 3)
ON DUPLICATE KEY UPDATE `username` = VALUES(`username`), `status` = 1, `type` = 3;
INSERT INTO `user_detail` (`id`, `type`, `name`, `role_id`)
VALUES (2, 3, '演示讲师', NULL)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `type` = 3;

-- Public student account used by the local front-end demonstration.
INSERT INTO `user` (`id`, `username`, `cell_phone`, `password`, `status`, `type`)
VALUES (3, 'demo', '13700000000', '$2b$12$IByF0GzRp4uihfSsp1nRJ.h2IiKtMEZDgg7IvesuqyPy4vJ0DWR5W', 1, 2)
ON DUPLICATE KEY UPDATE `username` = VALUES(`username`), `status` = 1, `type` = 2;
INSERT INTO `user_detail` (`id`, `type`, `name`, `role_id`)
VALUES (3, 2, '演示学员', NULL)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `type` = 2;
