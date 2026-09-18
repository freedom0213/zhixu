package com.zhixu.exam.domain.po;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 考试对题目的「引用」（草稿阶段）：只记题目 id 与分值 —— 分值归试卷，不入题库。
 * 存于 exam.items（JSON）。
 * </p>
 */
@Data
@Accessors(chain = true)
public class ExamItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 题目id
     */
    private Long questionId;

    /**
     * 本卷分值（同一道题在不同卷里可以不同分）
     */
    private Integer score;
}
