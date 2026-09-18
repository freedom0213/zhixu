package com.zhixu.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 我的作答记录列表项（学生端「在线考试」） */
@Data
@ApiModel("作答记录列表项")
public class ExamRecordPageVO {
    private Long id;
    private Long examId;
    private String examName;
    private String courseName;
    @ApiModelProperty("作答学生id")
    private Long studentId;
    @ApiModelProperty("学生姓名（仅讲师批改列表填充；学生自己看自己的记录时为 null）")
    private String studentName;
    @ApiModelProperty("类型，1：正式考试，2：随堂练习")
    private Integer examType;
    private Integer score;
    private Integer totalScore;
    private Integer correctCount;
    private Integer totalCount;
    private Integer passed;
    @ApiModelProperty("0：进行中（未交卷），1：已交卷，2：已复核")
    private Integer status;
    private Integer duration;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
}
