-- Local demo feature migration.
-- This file is intentionally additive and can be run against an existing
-- Docker MySQL volume. It does not drop or truncate application tables.

SET NAMES utf8mb4;

USE tj_learning;

CREATE TABLE IF NOT EXISTS `note` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `course_id` BIGINT NOT NULL,
  `chapter_id` BIGINT DEFAULT NULL,
  `section_id` BIGINT DEFAULT NULL,
  `note_moment` INT NOT NULL DEFAULT 0,
  `content` TEXT NOT NULL,
  `is_private` TINYINT NOT NULL DEFAULT 0,
  `hidden` TINYINT NOT NULL DEFAULT 0,
  `hidden_reason` VARCHAR(255) DEFAULT NULL,
  `author_id` BIGINT NOT NULL,
  `gathered_note_id` BIGINT DEFAULT NULL,
  `is_gathered` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_note_course_section` (`course_id`, `section_id`, `hidden`),
  KEY `idx_note_user` (`user_id`, `hidden`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `note_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `note_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `is_gathered` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_note_user` (`note_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `evaluation` (
  `id` BIGINT NOT NULL,
  `course_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `teacher_id` BIGINT DEFAULT NULL,
  `content_rating` TINYINT NOT NULL,
  `teaching_rating` TINYINT NOT NULL,
  `difficulty_rating` TINYINT NOT NULL,
  `value_rating` TINYINT NOT NULL,
  `overall_rating` DECIMAL(3,1) NOT NULL,
  `comment` VARCHAR(1000) NOT NULL,
  `anonymity` TINYINT NOT NULL DEFAULT 0,
  `help_count` INT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evaluation_user_course` (`user_id`, `course_id`),
  KEY `idx_evaluation_course` (`course_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

USE tj_promotion;

CREATE TABLE IF NOT EXISTS `coupon` (
  `id` BIGINT NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `type` TINYINT NOT NULL DEFAULT 1,
  `discount_type` TINYINT NOT NULL,
  `specific` TINYINT NOT NULL DEFAULT 0,
  `discount_value` INT NOT NULL,
  `threshold_amount` INT NOT NULL DEFAULT 0,
  `max_discount_amount` INT NOT NULL DEFAULT 0,
  `obtain_way` TINYINT NOT NULL DEFAULT 1,
  `issue_begin_time` DATETIME NOT NULL,
  `issue_end_time` DATETIME NOT NULL,
  `term_days` INT NOT NULL DEFAULT 30,
  `term_begin_time` DATETIME DEFAULT NULL,
  `term_end_time` DATETIME DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `total_num` INT NOT NULL DEFAULT 0,
  `issue_num` INT NOT NULL DEFAULT 0,
  `used_num` INT NOT NULL DEFAULT 0,
  `user_limit` INT NOT NULL DEFAULT 1,
  `ext_param` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creater` BIGINT DEFAULT NULL,
  `updater` BIGINT DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_coupon_status_time` (`status`, `issue_begin_time`, `issue_end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `coupon_scope` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `type` TINYINT NOT NULL DEFAULT 2,
  `coupon_id` BIGINT NOT NULL,
  `biz_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupon_scope` (`coupon_id`, `type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_coupon` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `coupon_id` BIGINT NOT NULL,
  `term_begin_time` DATETIME NOT NULL,
  `term_end_time` DATETIME NOT NULL,
  `used_time` DATETIME DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_coupon_user_status` (`user_id`, `status`, `create_time`),
  KEY `idx_user_coupon_coupon` (`coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `exchange_code` (
  `id` INT NOT NULL,
  `code` VARCHAR(32) NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `user_id` BIGINT DEFAULT NULL,
  `type` TINYINT NOT NULL DEFAULT 1,
  `exchange_target_id` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expired_time` DATETIME NOT NULL,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `coupon` (`id`, `name`, `type`, `discount_type`, `specific`, `discount_value`, `threshold_amount`, `max_discount_amount`, `obtain_way`, `issue_begin_time`, `issue_end_time`, `term_days`, `status`, `total_num`, `issue_num`, `used_num`, `user_limit`, `creater`, `updater`)
VALUES
  (7001, '知序新用户立减券', 1, 4, 0, 3000, 10000, 0, 1, NOW() - INTERVAL 1 DAY, '2099-12-31 23:59:59', 30, 3, 1000, 0, 0, 1, 1, 1),
  (7002, '后端课程 8 折券', 1, 2, 0, 80, 0, 5000, 1, NOW() - INTERVAL 1 DAY, '2099-12-31 23:59:59', 30, 3, 1000, 0, 0, 1, 1, 1)
ON DUPLICATE KEY UPDATE `status` = 3, `issue_end_time` = '2099-12-31 23:59:59';

-- A deterministic demo code. It is generated by the application algorithm and
-- may be replaced with a newly generated code when the promotion service runs.
INSERT INTO `exchange_code` (`id`, `code`, `status`, `type`, `exchange_target_id`, `expired_time`)
VALUES (700001, '7QJVLK5JR7', 1, 1, 7001, '2099-12-31 23:59:59')
ON DUPLICATE KEY UPDATE `code` = VALUES(`code`), `status` = 1, `exchange_target_id` = 7001,
  `expired_time` = '2099-12-31 23:59:59';

USE tj_learning;

CREATE TABLE IF NOT EXISTS `points_mall_item` (
  `id` BIGINT NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `icon` VARCHAR(500) DEFAULT NULL,
  `points` INT NOT NULL,
  `stock` INT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 1,
  `description` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `points_exchange_record` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `item_id` BIGINT NOT NULL,
  `points_used` INT NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `address` VARCHAR(255) DEFAULT NULL,
  `phone` VARCHAR(30) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_points_exchange_user` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `course_collect` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `course_id` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_collect` (`user_id`, `course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `points_board_season` (`id`, `name`, `begin_time`, `end_time`)
VALUES (1, '2026 秋季学习赛', '2026-09-01', '2026-12-31')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `end_time` = VALUES(`end_time`);

-- The application normally creates this table at the end of a season. Keep a
-- small local history table so the history tab is useful in the demo.
CREATE TABLE IF NOT EXISTS `points_board_1` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `points` INT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_points_board_1_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `points_board_1` (`id`, `user_id`, `points`)
VALUES (1, 3, 86), (2, 2, 72), (3, 1, 45)
ON DUPLICATE KEY UPDATE `user_id` = VALUES(`user_id`), `points` = VALUES(`points`);

INSERT INTO `points_mall_item` (`id`, `name`, `icon`, `points`, `stock`, `description`)
VALUES
  (8101, '知序学习笔记本', '', 100, 99, '用于记录学习计划的实体笔记本'),
  (8102, '课程优惠券 10 元', '', 200, 99, '可抵扣本地演示课程订单金额'),
  (8103, '知序学习徽章', '', 300, 50, '完成学习挑战后兑换专属徽章')
ON DUPLICATE KEY UPDATE `stock` = VALUES(`stock`), `status` = 1;

INSERT INTO `points_record` (`id`, `user_id`, `type`, `points`, `create_time`)
VALUES
  (900001, 3, 1, 40, NOW() - INTERVAL 2 DAY),
  (900002, 3, 2, 2, NOW() - INTERVAL 1 DAY),
  (900003, 3, 4, 8, NOW() - INTERVAL 1 DAY),
  (900004, 2, 1, 35, NOW() - INTERVAL 2 DAY),
  (900005, 2, 2, 2, NOW() - INTERVAL 1 DAY),
  (900006, 1, 1, 20, NOW() - INTERVAL 3 DAY)
ON DUPLICATE KEY UPDATE `points` = VALUES(`points`), `create_time` = VALUES(`create_time`);

USE tj_course;

INSERT INTO `category` (`id`, `name`, `parent_id`, `level`, `priority`, `status`, `creater`, `updater`)
VALUES
  (4, 'Spring Cloud', 1, 2, 2, 1, 1, 1),
  (5, 'Spring Cloud 实战', 4, 3, 1, 1, 1, 1),
  (6, 'MySQL', 1, 2, 3, 1, 1, 1),
  (7, 'MySQL 数据库', 6, 3, 1, 1, 1, 1),
  (8, 'Redis', 1, 2, 4, 1, 1, 1),
  (9, 'Redis 高级应用', 8, 3, 1, 1, 1, 1),
  (10, 'Python', 1, 2, 5, 1, 1, 1),
  (11, 'Python 编程', 10, 3, 1, 1, 1, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `status` = 1, `deleted` = 0;

INSERT INTO `category` (`id`, `name`, `parent_id`, `level`, `priority`, `status`, `creater`, `updater`)
VALUES (12, 'Java 核心', 2, 3, 2, 1, 1, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `status` = 1, `deleted` = 0;

INSERT INTO `course` (`id`, `name`, `course_type`, `cover_url`, `first_cate_id`, `second_cate_id`, `third_cate_id`, `free`, `price`, `template_type`, `status`, `purchase_start_time`, `purchase_end_time`, `step`, `score`, `media_duration`, `valid_duration`, `section_num`, `publish_times`, `publish_time`, `creater`, `updater`)
VALUES
  (1003, 'Java 集合与并发编程', 2, '', 1, 2, 12, 1, 0, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 47, 2400, 12, 3, 1, NOW(), 1, 1),
  (1004, 'Java 虚拟机性能调优', 2, '', 1, 2, 12, 0, 12900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 46, 3000, 12, 3, 1, NOW(), 1, 1),
  (1005, 'Java 设计模式实战', 2, '', 1, 2, 12, 0, 9900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 49, 2700, 12, 3, 1, NOW(), 1, 1),
  (1011, 'Spring Cloud 服务治理', 2, '', 1, 4, 5, 0, 15900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 48, 3300, 12, 3, 1, NOW(), 1, 1),
  (1012, 'Spring Cloud Alibaba 实战', 2, '', 1, 4, 5, 0, 19900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 46, 3600, 12, 3, 1, NOW(), 1, 1),
  (1013, '微服务网关与容错', 2, '', 1, 4, 5, 1, 0, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 45, 2100, 12, 3, 1, NOW(), 1, 1),
  (1021, 'MySQL 从入门到索引优化', 2, '', 1, 6, 7, 1, 0, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 48, 3000, 12, 3, 1, NOW(), 1, 1),
  (1022, 'MySQL 事务与锁机制', 2, '', 1, 6, 7, 0, 11900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 47, 2700, 12, 3, 1, NOW(), 1, 1),
  (1023, 'MySQL 高可用架构', 2, '', 1, 6, 7, 0, 17900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 46, 3300, 12, 3, 1, NOW(), 1, 1),
  (1031, 'Redis 数据结构精讲', 2, '', 1, 8, 9, 1, 0, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 49, 2400, 12, 3, 1, NOW(), 1, 1),
  (1032, 'Redis 缓存与分布式锁', 2, '', 1, 8, 9, 0, 10900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 48, 2700, 12, 3, 1, NOW(), 1, 1),
  (1033, 'Redis 集群与高并发', 2, '', 1, 8, 9, 0, 16900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 47, 3300, 12, 3, 1, NOW(), 1, 1),
  (1041, 'Python 编程基础', 2, '', 1, 10, 11, 1, 0, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 49, 2400, 12, 3, 1, NOW(), 1, 1),
  (1042, 'Python 数据处理入门', 2, '', 1, 10, 11, 0, 9900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 47, 2700, 12, 3, 1, NOW(), 1, 1),
  (1043, 'Python Web 开发实战', 2, '', 1, 10, 11, 0, 14900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 46, 3300, 12, 3, 1, NOW(), 1, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `free` = VALUES(`free`), `price` = VALUES(`price`), `status` = 2, `deleted` = 0;

INSERT INTO `course_content` (`id`, `course_introduce`, `use_people`, `course_detail`, `creater`, `updater`)
SELECT id, CONCAT('本地演示课程：', name), '希望快速掌握后端开发实践的学习者', CONCAT('<p>围绕 ', name, ' 安排循序渐进的课程内容，可直接体验课程目录和购买流程。</p>'), 1, 1
FROM `course`
WHERE id BETWEEN 1001 AND 1043
ON DUPLICATE KEY UPDATE `course_introduce` = VALUES(`course_introduce`), `course_detail` = VALUES(`course_detail`), `deleted` = 0;

INSERT INTO `course_catalogue` (`id`, `name`, `trailer`, `course_id`, `type`, `parent_catalogue_id`, `video_name`, `media_duration`, `c_index`, `creater`, `updater`)
SELECT (id * 10) + 1, '第一章 核心概念', 1, id, 1, 0, NULL, 900, 1, 1, 1 FROM `course` WHERE id BETWEEN 1001 AND 1043
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `deleted` = 0;
INSERT INTO `course_catalogue` (`id`, `name`, `trailer`, `course_id`, `type`, `parent_catalogue_id`, `video_name`, `media_duration`, `c_index`, `creater`, `updater`)
SELECT (id * 10) + 2, '环境搭建与第一个示例', 1, id, 2, (id * 10) + 1, '环境搭建与第一个示例', 600, 1, 1, 1 FROM `course` WHERE id BETWEEN 1001 AND 1043
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `deleted` = 0;
INSERT INTO `course_catalogue` (`id`, `name`, `trailer`, `course_id`, `type`, `parent_catalogue_id`, `video_name`, `media_duration`, `c_index`, `creater`, `updater`)
SELECT (id * 10) + 3, '项目实战与常见问题', 0, id, 2, (id * 10) + 1, '项目实战与常见问题', 900, 2, 1, 1 FROM `course` WHERE id BETWEEN 1001 AND 1043
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `deleted` = 0;

INSERT INTO `course_teacher` (`course_id`, `teacher_id`, `is_show`, `c_index`, `creater`, `updater`)
SELECT id, 2, 1, 1, 1, 1 FROM `course` WHERE id BETWEEN 1001 AND 1043
ON DUPLICATE KEY UPDATE `is_show` = 1, `deleted` = 0;
