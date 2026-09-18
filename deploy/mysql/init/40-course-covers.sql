-- 40-course-covers.sql
-- 为演示课程批量设置封面图（由 deploy/generate_covers.py 生成的 SVG）
-- 封面文件位于前端静态目录：tj-front/tj-protal/public/covers/
USE tj_course;

UPDATE `course` SET `cover_url` = '/covers/1001.svg' WHERE `id` = 1001;
UPDATE `course` SET `cover_url` = '/covers/1002.svg' WHERE `id` = 1002;
UPDATE `course` SET `cover_url` = '/covers/1003.svg' WHERE `id` = 1003;
UPDATE `course` SET `cover_url` = '/covers/1004.svg' WHERE `id` = 1004;
UPDATE `course` SET `cover_url` = '/covers/1005.svg' WHERE `id` = 1005;
UPDATE `course` SET `cover_url` = '/covers/1011.svg' WHERE `id` = 1011;
UPDATE `course` SET `cover_url` = '/covers/1012.svg' WHERE `id` = 1012;
UPDATE `course` SET `cover_url` = '/covers/1013.svg' WHERE `id` = 1013;
UPDATE `course` SET `cover_url` = '/covers/1021.svg' WHERE `id` = 1021;
UPDATE `course` SET `cover_url` = '/covers/1022.svg' WHERE `id` = 1022;
UPDATE `course` SET `cover_url` = '/covers/1023.svg' WHERE `id` = 1023;
UPDATE `course` SET `cover_url` = '/covers/1031.svg' WHERE `id` = 1031;
UPDATE `course` SET `cover_url` = '/covers/1032.svg' WHERE `id` = 1032;
UPDATE `course` SET `cover_url` = '/covers/1033.svg' WHERE `id` = 1033;
UPDATE `course` SET `cover_url` = '/covers/1041.svg' WHERE `id` = 1041;
UPDATE `course` SET `cover_url` = '/covers/1042.svg' WHERE `id` = 1042;
UPDATE `course` SET `cover_url` = '/covers/1043.svg' WHERE `id` = 1043;
