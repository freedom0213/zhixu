package com.zhixu.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixu.exam.domain.po.ExamRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ExamRecordMapper extends BaseMapper<ExamRecord> {

    /**
     * 回写题库计数（p14 §4.1 的落点）：一次批量，不做 N 次单条更新。
     * 每道题 answer_times +1；答对的再 correct_times +1。
     * 只由「真正交卷成功」这一条路径调用（幂等返回的场景不调）。
     */
    @Update("<script>"
            + "UPDATE question SET "
            + "answer_times = answer_times + 1, "
            + "correct_times = correct_times + (CASE WHEN id IN "
            + "<foreach collection='correctIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach>"
            + " THEN 1 ELSE 0 END) "
            + "WHERE id IN "
            + "<foreach collection='allIds' item='qid' open='(' separator=',' close=')'>#{qid}</foreach>"
            + "</script>")
    int bumpQuestionCounters(@Param("allIds") List<Long> allIds,
                             @Param("correctIds") List<Long> correctIds);

    /**
     * 全错（或一道都没对）的情况：只加被作答次数。
     * 单独一个方法是因为 `id IN ()` 是非法 SQL —— 空集合不能拼进 CASE WHEN。
     */
    @Update("<script>"
            + "UPDATE question SET answer_times = answer_times + 1 WHERE id IN "
            + "<foreach collection='allIds' item='qid' open='(' separator=',' close=')'>#{qid}</foreach>"
            + "</script>")
    int bumpAnswerTimesOnly(@Param("allIds") List<Long> allIds);
}
