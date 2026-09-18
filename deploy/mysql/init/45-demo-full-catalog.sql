-- 45-demo-full-catalog.sql
-- 知序学堂全分类课程补齐：每个三级分类至少 1 门课（32 门，ID 2xxx 段）
-- 依赖：35-category-v2.sql（新分类体系 101-105 / 111-192 / 2xx）
-- 幂等：全部 INSERT ... ON DUPLICATE KEY UPDATE / UPDATE，可重复执行
-- 注意：中文种子数据必须以 utf8mb4 客户端字符集导入（docker exec 时加 --default-character-set=utf8mb4）

USE tj_course;
SET NAMES utf8mb4;

-- ===== 1. 新增课程（course）=====
-- 格式: id, name, course_type, cover_url, first_cate_id, second_cate_id, third_cate_id, free, price, ...
INSERT INTO `course` (`id`, `name`, `course_type`, `cover_url`, `first_cate_id`, `second_cate_id`, `third_cate_id`, `free`, `price`, `template_type`, `status`, `purchase_start_time`, `purchase_end_time`, `step`, `score`, `media_duration`, `valid_duration`, `section_num`, `publish_times`, `publish_time`, `creater`, `updater`)
VALUES
  -- 计算机基础(111)：211-214
  (2101, '操作系统原理与实践入门', 2, '/covers/2101.svg', 101, 111, 211, 0, 8900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 46, 3000, 12, 3, 1, NOW(), 1, 1),
  (2102, '计算机网络：从 TCP/IP 到 HTTP', 2, '/covers/2102.svg', 101, 111, 212, 1, 0, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 47, 2700, 12, 3, 1, NOW(), 1, 1),
  (2103, '数据结构与链表实战', 2, '/covers/2103.svg', 101, 111, 213, 0, 9900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 48, 2400, 12, 3, 1, NOW(), 1, 1),
  (2104, '算法入门：排序与查找', 2, '/covers/2104.svg', 101, 111, 214, 1, 0, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 49, 2700, 12, 3, 1, NOW(), 1, 1),
  -- 编程语言(112)：221-223
  (2211, 'Java 21 新特性实战', 2, '/covers/2211.svg', 101, 112, 221, 0, 12900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 47, 2700, 12, 3, 1, NOW(), 1, 1),
  (2212, 'Python 自动化脚本实战', 2, '/covers/2212.svg', 101, 112, 222, 0, 9900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 46, 2400, 12, 3, 1, NOW(), 1, 1),
  (2213, 'Go 语言并发编程入门', 2, '/covers/2213.svg', 101, 112, 223, 1, 0, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 48, 3000, 12, 3, 1, NOW(), 1, 1),
  -- UI设计(141)：241-244
  (2411, 'UI 设计基础：从临摹到原创', 2, '/covers/2411.svg', 102, 141, 241, 1, 0, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 47, 2700, 12, 3, 1, NOW(), 1, 1),
  (2412, 'Figma 高效设计工作流', 2, '/covers/2412.svg', 102, 141, 242, 0, 11900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 48, 3000, 12, 3, 1, NOW(), 1, 1),
  (2413, '界面设计规范与组件库搭建', 2, '/covers/2413.svg', 102, 141, 243, 0, 13900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 46, 3300, 12, 3, 1, NOW(), 1, 1),
  (2414, '交互设计入门：用户旅程与原型', 2, '/covers/2414.svg', 102, 141, 244, 0, 10900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 47, 2700, 12, 3, 1, NOW(), 1, 1),
  -- AI创意(142)：251-254
  (2511, 'AI 绘画：Stable Diffusion 实战', 2, '/covers/2511.svg', 102, 142, 251, 0, 14900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 48, 3300, 12, 3, 1, NOW(), 1, 1),
  (2512, 'AI 设计工具全指南', 2, '/covers/2512.svg', 102, 142, 252, 0, 9900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 46, 2700, 12, 3, 1, NOW(), 1, 1),
  (2513, 'Prompt 工程入门与实践', 2, '/covers/2513.svg', 102, 142, 253, 1, 0, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 49, 2400, 12, 3, 1, NOW(), 1, 1),
  (2514, 'AI 工作流：让重复工作自动化', 2, '/covers/2514.svg', 102, 142, 254, 0, 12900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 47, 3000, 12, 3, 1, NOW(), 1, 1),
  -- 办公软件(161)：261-264
  (2611, 'Word 长文档排版实战', 2, '/covers/2611.svg', 103, 161, 261, 0, 6900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 47, 2100, 12, 3, 1, NOW(), 1, 1),
  (2612, 'Excel 函数与数据透视表', 2, '/covers/2612.svg', 103, 161, 262, 1, 0, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 49, 3000, 12, 3, 1, NOW(), 1, 1),
  (2613, 'PPT 演示设计进阶', 2, '/covers/2613.svg', 103, 161, 263, 0, 7900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 46, 2400, 12, 3, 1, NOW(), 1, 1),
  (2614, 'WPS 办公全家桶速成', 2, '/covers/2614.svg', 103, 161, 264, 0, 5900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 45, 1800, 12, 3, 1, NOW(), 1, 1),
  -- 工作效率(162)：271-274
  (2711, '时间管理：告别拖延症', 2, '/covers/2711.svg', 103, 162, 271, 1, 0, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 48, 1800, 12, 3, 1, NOW(), 1, 1),
  (2712, '任务管理：GTD 实践指南', 2, '/covers/2712.svg', 103, 162, 272, 0, 6900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 46, 2100, 12, 3, 1, NOW(), 1, 1),
  (2713, '知识管理：搭建个人第二大脑', 2, '/covers/2713.svg', 103, 162, 273, 0, 8900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 47, 2400, 12, 3, 1, NOW(), 1, 1),
  (2714, 'AI 办公效率革命', 2, '/covers/2714.svg', 103, 162, 274, 0, 9900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 48, 2400, 12, 3, 1, NOW(), 1, 1),
  -- 英语(181)：281-284
  (2811, '英语基础：音标与核心语法', 2, '/covers/2811.svg', 104, 181, 281, 1, 0, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 47, 3000, 12, 3, 1, NOW(), 1, 1),
  (2812, '英语听力专项训练', 2, '/covers/2812.svg', 104, 181, 282, 0, 7900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 46, 2700, 12, 3, 1, NOW(), 1, 1),
  (2813, '英语口语：日常对话突破', 2, '/covers/2813.svg', 104, 181, 283, 0, 8900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 47, 2700, 12, 3, 1, NOW(), 1, 1),
  (2814, '商务英语：职场沟通实战', 2, '/covers/2814.svg', 104, 181, 284, 0, 11900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 48, 3000, 12, 3, 1, NOW(), 1, 1),
  -- 大学考试(191)：291-293 / IT认证(192)：294-295
  (2911, 'CET-4 词汇与真题精讲', 2, '/covers/2911.svg', 105, 191, 291, 1, 0, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 48, 3300, 12, 3, 1, NOW(), 1, 1),
  (2912, 'CET-6 听力阅读冲刺', 2, '/covers/2912.svg', 105, 191, 292, 0, 9900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 46, 2700, 12, 3, 1, NOW(), 1, 1),
  (2913, '计算机等级考试一级通关', 2, '/covers/2913.svg', 105, 191, 293, 0, 6900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 47, 2400, 12, 3, 1, NOW(), 1, 1),
  (2914, '软考中级：系统集成项目管理', 2, '/covers/2914.svg', 105, 192, 294, 0, 15900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 47, 3600, 12, 3, 1, NOW(), 1, 1),
  (2915, '云计算基础与上云实践', 2, '/covers/2915.svg', 105, 192, 295, 0, 13900, 1, 2, NOW(), '2099-12-31 23:59:59', 5, 46, 3300, 12, 3, 1, NOW(), 1, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `cover_url` = VALUES(`cover_url`), `free` = VALUES(`free`), `price` = VALUES(`price`), `status` = 2, `deleted` = 0;

-- ===== 2. 课程详情（course_content）=====
INSERT INTO `course_content` (`id`, `course_introduce`, `use_people`, `course_detail`, `creater`, `updater`)
SELECT id, CONCAT('本地演示课程：', name), '希望系统学习该方向知识的学习者', CONCAT('<p>围绕 ', name, ' 安排循序渐进的课程内容，包含理论讲解与动手练习，可直接体验课程目录和购买流程。</p>'), 1, 1
FROM `course`
WHERE id BETWEEN 2101 AND 2915
ON DUPLICATE KEY UPDATE `course_introduce` = VALUES(`course_introduce`), `course_detail` = VALUES(`course_detail`), `deleted` = 0;

-- ===== 3. 课程目录（course_catalogue）：每门课 1 章 2 节 =====
INSERT INTO `course_catalogue` (`id`, `name`, `trailer`, `course_id`, `type`, `parent_catalogue_id`, `video_name`, `media_duration`, `c_index`, `creater`, `updater`)
SELECT (id * 10) + 1, '第一章 课程导学', 1, id, 1, 0, NULL, 900, 1, 1, 1 FROM `course` WHERE id BETWEEN 2101 AND 2915
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `deleted` = 0;

INSERT INTO `course_catalogue` (`id`, `name`, `trailer`, `course_id`, `type`, `parent_catalogue_id`, `video_name`, `media_duration`, `c_index`, `creater`, `updater`)
SELECT (id * 10) + 2, '核心概念与快速上手', 1, id, 2, (id * 10) + 1, '核心概念与快速上手', 900, 1, 1, 1 FROM `course` WHERE id BETWEEN 2101 AND 2915
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `deleted` = 0;

INSERT INTO `course_catalogue` (`id`, `name`, `trailer`, `course_id`, `type`, `parent_catalogue_id`, `video_name`, `media_duration`, `c_index`, `creater`, `updater`)
SELECT (id * 10) + 3, '实战练习与小结', 1, id, 2, (id * 10) + 1, '实战练习与小结', 900, 2, 1, 1 FROM `course` WHERE id BETWEEN 2101 AND 2915
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `deleted` = 0;

-- ===== 4. 课程教师（course_teacher）：统一挂到演示教师 =====
INSERT INTO `course_teacher` (`id`, `course_id`, `teacher_id`, `is_show`, `c_index`, `creater`, `updater`)
SELECT id, id, 2, 1, 1, 1, 1 FROM `course` WHERE id BETWEEN 2101 AND 2915
ON DUPLICATE KEY UPDATE `is_show` = VALUES(`is_show`), `deleted` = 0;

-- ===== 5. 老课程分类迁移到新体系（幂等，条件收窄防止误改）=====
-- Java 课(1003/1004/1005 原 third=12 Java核心) → 编程语言/Java(221)
UPDATE `course` SET `second_cate_id` = 112, `third_cate_id` = 221
WHERE id IN (1003, 1004, 1005) AND `third_cate_id` = 12;
-- Java 微服务课(1001) 保留在"后端开发→微服务"，同时 second 指向 Java 保持入口可达
-- Python 课(1041-1043 原 second=10/third=11) → 编程语言/Python(222)
UPDATE `course` SET `second_cate_id` = 112, `third_cate_id` = 222
WHERE id IN (1041, 1042, 1043) AND `third_cate_id` = 11;
