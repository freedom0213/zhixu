package com.zhixu.exam.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixu.exam.mapper.ExamSnapshotItemTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 考试 / 练习的作答记录（p14：一场考试一个学生一条）
 * </p>
 *
 * 为什么用唯一键 uk_exam_student 而不是靠代码判断「考过没有」：
 *   旧 UI 明说「考试只能考一次」，这是业务规则 —— 让它成为数据库事实，
 *   并发重复提交时数据库自己会挡住，不依赖应用层的"先查再写"。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "exam_record", autoResultMap = true)
public class ExamRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（雪花） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 考试/练习id */
    private Long examId;

    /** 作答学生（用户id） */
    private Long studentId;

    /** 得分 */
    private Integer score;

    /** 卷面总分 */
    private Integer totalScore;

    /** 答对题数 */
    private Integer correctCount;

    /** 总题数 */
    private Integer totalCount;

    /** 是否及格（按 exam.pass_score） */
    private Integer passed;

    /** 开始作答时间 */
    private LocalDateTime startTime;

    /** 交卷时间 */
    private LocalDateTime finishTime;

    /** 0：进行中（开始但未交卷），1：已交卷，2：已复核 */
    private Integer status;

    /** 作答时的试卷版本 */
    private String paperVersion;

    /**
     * 开始作答瞬间冻结的试卷快照（回看与判分都用它）。
     * ⚠️ 这里必须存副本而不是引用 exam.snapshot_items：试卷可以被重新发布，
     *    那时 exam 上的快照会被覆盖 —— 学生回看到的就不是他当时做的那份题了。
     */
    @TableField(typeHandler = ExamSnapshotItemTypeHandler.class)
    private List<ExamSnapshotItem> snapshotItems;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
