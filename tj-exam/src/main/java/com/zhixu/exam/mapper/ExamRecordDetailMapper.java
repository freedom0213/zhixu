package com.zhixu.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixu.exam.domain.po.ExamRecordDetail;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface ExamRecordDetailMapper extends BaseMapper<ExamRecordDetail> {

    /**
     * 逐题作答统计（p15 讲师端「统计」页）。
     * 只计**已交卷**的记录（status >= 1）；correct 是 0/1，所以 SUM 直接就是答对数。
     * 一次 group by 算完全卷，不做「每题查一次」的 N+1。
     */
    @Select("SELECT d.question_id AS questionId, COUNT(*) AS attempts, SUM(d.correct) AS correctCount "
            + "FROM exam_record_detail d JOIN exam_record r ON r.id = d.record_id "
            + "WHERE r.exam_id = #{examId} AND r.status >= 1 "
            + "GROUP BY d.question_id")
    List<Map<String, Object>> countPerQuestion(@Param("examId") Long examId);
}
