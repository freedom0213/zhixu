package com.zhixu.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixu.exam.domain.po.Exam;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface ExamMapper extends BaseMapper<Exam> {

    /**
     * 统计引用了某道题的考试（契约 §4.2 GET /questions/{id}/usage）。
     * items 是 JSON 数组 [{questionId, score}]，JSON_CONTAINS 对数组元素做「包含」判断，
     * 只带 questionId 键即可命中（score 被忽略）。
     */
    @Select("SELECT id, name FROM exam WHERE JSON_CONTAINS(items, CONCAT('{\"questionId\":', #{questionId}, '}'))")
    List<Map<String, Object>> selectExamsUsingQuestion(@Param("questionId") Long questionId);

    /**
     * 一次请求内把「每道题的试卷引用数」算出来（p13 §A5）。
     * 用相关子查询而不是前端逐题调 /usage：列表页 20 行只发 1 个请求，不做 N+1。
     * ⚠️ 规模提醒：这是「每题扫一遍试卷 JSON」，题量到十万级要换成维护计数列。
     */
    @Select("<script>SELECT q.id AS questionId, "
            + "(SELECT COUNT(*) FROM exam e WHERE JSON_CONTAINS(e.items, CONCAT('{\"questionId\":', q.id, '}'))) AS refCount "
            + "FROM question q WHERE q.id IN "
            + "<foreach collection='ids' item='qid' open='(' separator=',' close=')'>#{qid}</foreach>"
            + "</script>")
    List<Map<String, Object>> countRefsByQuestionIds(@Param("ids") List<Long> ids);

    /** 交卷人数 +1（p14：把 submitted_count 从"批改阶段回填"的死字段变成真数字） */
    @Update("UPDATE exam SET submitted_count = submitted_count + 1 WHERE id = #{examId}")
    int bumpSubmittedCount(@Param("examId") Long examId);

    /** 按小节找随堂练习（p14：学生从课程学习页的「考试」小节进入作答） */
    @Select("SELECT * FROM exam WHERE section_id = #{sectionId} ORDER BY id DESC LIMIT 1")
    Exam selectBySection(@Param("sectionId") Long sectionId);
}
