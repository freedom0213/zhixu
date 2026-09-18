package com.zhixu.exam.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 作答明细（p14：每题一行）
 * </p>
 *
 * question_id 指向**题库题**（不是试卷快照）—— 交卷判分后要按它回写
 * question.answer_times / correct_times，让题库的「正确率」有真数据。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("exam_record_detail")
public class ExamRecordDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（雪花） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属作答记录（exam_record.id） */
    private Long recordId;

    /** 题目id（题库题id） */
    private Long questionId;

    /** 学生答案（选项编号升序串，如 "1,3"） */
    private String answer;

    /** 是否正确 */
    private Integer correct;

    /** 本题得分 */
    private Integer score;

    /** 学生是否标记待查（答题卡用） */
    private Integer marked;
}
