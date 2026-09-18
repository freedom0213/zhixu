package com.zhixu.exam.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zhixu.exam.domain.po.ExamItem;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 考试表单（契约 §9：POST /exams 新建草稿、PUT /exams/{id} 更新草稿）
 * 草稿只存「引用」（items：questionId + 分值），发布时才生成快照。
 * </p>
 */
@Data
@ApiModel(description = "考试表单")
public class ExamFormDTO {

    @NotBlank(message = "考试名称不能为空")
    @ApiModelProperty("考试名称")
    private String name;

    @NotNull(message = "关联课程不能为空")
    @ApiModelProperty("关联课程id")
    private Long courseId;

    @ApiModelProperty("课程名称（冗余存储，前端一并传入；缺省由服务端留空）")
    private String courseName;

    @ApiModelProperty("类型，1：正式考试，2：随堂练习")
    private Integer examType = 1;

    @ApiModelProperty("及格线")
    private Integer passScore = 60;

    @ApiModelProperty("考试时长（分钟）")
    private Integer duration = 90;

    @ApiModelProperty("考试须知")
    private String notice;

    @ApiModelProperty("开始时间，格式 yyyy-MM-dd HH:mm")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime startAt;

    @ApiModelProperty("结束时间，格式 yyyy-MM-dd HH:mm")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime endAt;

    @ApiModelProperty("试卷题目引用 [{questionId, score}]")
    private List<ExamItem> items;

    @ApiModelProperty("发布方式，now：立即，draft：存草稿，scheduled：定时（仅发布动作使用）")
    private String publishMode = "now";
}
