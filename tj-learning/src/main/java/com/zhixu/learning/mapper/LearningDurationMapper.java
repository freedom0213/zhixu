package com.zhixu.learning.mapper;

import com.zhixu.learning.domain.vo.LearningDurationDayVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/** 学习时长（按天聚合）· P27 */
@Mapper
public interface LearningDurationMapper {

    /** 累加一次观看时长（同一天同课程累加；唯一键保证**幂等写入**，不会写出多行） */
    @Insert("INSERT INTO learning_duration (user_id, course_id, learn_date, duration_sec) "
            + "VALUES (#{userId}, #{courseId}, #{learnDate}, #{sec}) "
            + "ON DUPLICATE KEY UPDATE duration_sec = duration_sec + #{sec}, update_time = NOW()")
    int accumulate(@Param("userId") Long userId,
                   @Param("courseId") Long courseId,
                   @Param("learnDate") LocalDate learnDate,
                   @Param("sec") int sec);

    /** 窗口内按天明细（只返回有记录的日期） */
    @Select("SELECT learn_date AS learnDate, SUM(duration_sec) AS durationSec "
            + "FROM learning_duration "
            + "WHERE user_id = #{userId} AND learn_date >= #{from} "
            + "GROUP BY learn_date ORDER BY learn_date")
    List<LearningDurationDayVO> selectDailySince(@Param("userId") Long userId,
                                                 @Param("from") LocalDate from);

    /** 全部时间累计 */
    @Select("SELECT IFNULL(SUM(duration_sec), 0) FROM learning_duration WHERE user_id = #{userId}")
    Integer sumAll(@Param("userId") Long userId);
}
