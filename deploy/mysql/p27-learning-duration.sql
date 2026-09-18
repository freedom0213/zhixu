-- =============================================================================
-- P27 · 学习时长（按天聚合）
-- -----------------------------------------------------------------------------
-- 热力图与「累计/日均学习」需要**按天**的时长数据，learning_lesson 只有累计小节数、
-- learning_record 只有每节的最后播放位置，都推不出「每天学了多久」。
-- 所以单开一张按 (user, course, date) 聚合的表，上报时增量累加（幂等，可重复执行）。
-- =============================================================================
CREATE TABLE IF NOT EXISTS learning_duration (
    id           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT   NOT NULL COMMENT '用户id',
    course_id    BIGINT   NOT NULL DEFAULT 0 COMMENT '课程id（取不到时记 0）',
    learn_date   DATE     NOT NULL COMMENT '学习日期',
    duration_sec INT      NOT NULL DEFAULT 0 COMMENT '当天累计学习时长（秒）',
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_course_date (user_id, course_id, learn_date),
    KEY idx_user_date (user_id, learn_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学习时长（按天聚合）';
