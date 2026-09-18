package com.zhixu.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 考试分页数据（契约 §9 GET /exams/page）
 * </p>
 */
@Data
@ApiModel(description = "考试分页数据")
public class ExamPageVO {

    @ApiModelProperty("考试id")
    private Long id;

    @ApiModelProperty("考试名称")
    private String name;

    @ApiModelProperty("课程名称")
    private String courseName;

    @ApiModelProperty("状态，0：草稿，1：已发布，2：批改中，3：已结束")
    private Integer status;

    @ApiModelProperty("类型，1：正式考试，2：随堂练习")
    private Integer examType;

    @ApiModelProperty("题量")
    private Integer itemCount;

    @ApiModelProperty("总分（引用 items 的分值合计）")
    private Integer totalScore;

    @ApiModelProperty("截止时间")
    private LocalDateTime endAt;

    @ApiModelProperty("已提交人数（批改阶段回填，此前恒 0）")
    private Integer submittedCount;

    @ApiModelProperty("引用 items 原文（编辑草稿时需要）")
    private List<?> items;
}
