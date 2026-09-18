package com.zhixu.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 待批改列表的一行 = 一场考试（P18）。
 * -----------------------------------------------------------------------------
 * 讲师视角的「我现在有哪些卷子要处理」。数字一律从 exam_record 明细数出来（单一数据源），
 * **不含** status=0 的「进行中」记录 —— 学生点了开始没交卷的不该算进"待批改"。
 */
@Data
@Accessors(chain = true)
@ApiModel("待批改 · 一场考试")
public class PendingReviewItemVO {

    private Long examId;
    private String examName;
    private Long courseId;
    private String courseName;
    @ApiModelProperty("1 正式考试 / 2 随堂练习（P17 起不再产生 2）")
    private Integer examType;
    @ApiModelProperty("题量（读作答时冻结的快照）")
    private Integer questionCount;
    @ApiModelProperty("卷面总分（读快照求和）")
    private Integer totalScore;
    @ApiModelProperty("已交卷份数（含已复核）")
    private Integer submittedCount;
    @ApiModelProperty("待复核份数 = 已交卷 - 已复核")
    private Integer pendingCount;
    @ApiModelProperty("已复核份数")
    private Integer reviewedCount;
    @ApiModelProperty("最近一次交卷时间")
    private LocalDateTime lastSubmitTime;
}
