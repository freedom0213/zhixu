package com.zhixu.exam.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixu.exam.mapper.ExamItemTypeHandler;
import com.zhixu.exam.mapper.ExamSnapshotItemTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 考试（契约 §9 的核心数据模型：草稿存引用，发布才生成快照）
 * </p>
 *
 * @see ExamItem   草稿阶段的引用：只记 questionId + 分值
 * @see ExamSnapshotItem 发布瞬间冻结的完整题目副本
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "exam", autoResultMap = true)
public class Exam implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 考试名称
     */
    private String name;

    /**
     * 关联课程id
     */
    private Long courseId;

    /**
     * 课程名称（冗余，避免跨库 join）
     */
    private String courseName;

    /**
     * 随堂练习挂在哪个小节（course_catalogue.id；exam_type=2 时有值，正式考试为 null）
     * —— 学生从课程学习页点「考试类型的小节」进来时，就是靠这一列找到试卷
     */
    private Long sectionId;

    /**
     * 状态，0：草稿，1：已发布，2：批改中，3：已结束
     */
    private Integer status;

    /**
     * 类型，1：正式考试，2：随堂练习
     */
    private Integer examType;

    /**
     * 及格线
     */
    private Integer passScore;

    /**
     * 考试时长（分钟）
     */
    private Integer duration;

    /**
     * 考试须知
     */
    private String notice;

    /**
     * 开始时间
     */
    private LocalDateTime startAt;

    /**
     * 结束时间
     */
    private LocalDateTime endAt;

    /**
     * 引用：[{questionId, score}] —— 草稿跟着题库变
     */
    @TableField(typeHandler = ExamItemTypeHandler.class)
    private List<ExamItem> items;

    /**
     * 快照：发布瞬间冻结的完整题目内容，发布前为 NULL —— 之后题库怎么改都不影响
     */
    @TableField(typeHandler = ExamSnapshotItemTypeHandler.class)
    private List<ExamSnapshotItem> snapshotItems;

    /**
     * 试卷版本，发布后为 v1
     */
    private String paperVersion;

    /**
     * 冻结时间
     */
    private LocalDateTime snapshotAt;

    /**
     * 已提交人数（批改/统计阶段回填，本轮恒 0）
     */
    private Integer submittedCount;

    /**
     * 创建人
     */
    private Long creater;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
