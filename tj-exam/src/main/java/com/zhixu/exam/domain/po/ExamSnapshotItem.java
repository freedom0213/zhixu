package com.zhixu.exam.domain.po;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 试卷快照条目（发布瞬间把题目当时的内容冻结成副本），存于 exam.snapshotItems（JSON）。
 * 之后题库怎么改：卷面 / 学生答卷 / 批改 / 统计全部读这里，不受影响（契约 §9.2）。
 * </p>
 */
@Data
@Accessors(chain = true)
public class ExamSnapshotItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long questionId;

    private Integer score;

    /**
     * 题目类型，1：单选题，2：多选题（与 question.type 同口径）
     */
    private Integer type;

    /**
     * 题干（当时的内容）
     */
    private String stem;

    /**
     * 选项（当时的完整副本）
     */
    private List<String> options;

    /**
     * 正确答案（当时的副本，数字编号串如 "1" 或 "1,3"）
     */
    private String answer;

    private String analysis;

    /**
     * 难度，1：简单，2：中等，3：困难
     */
    private Integer difficulty;

    /**
     * 知识点（题目-知识点关联表就绪前为空数组）
     */
    private List<String> knowledgePoints;

    private Long courseId;
}
