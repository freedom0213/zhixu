package com.zhixu.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 学生视角：某门课程下**已发布**的试卷（P17）。
 * -----------------------------------------------------------------------------
 * 学生端两处共用它：课程学习页右侧「考试」页签 + 课程详情页的「课程试卷」。
 * 带上「我的作答状态」，学生一眼能看出哪场没考、哪场考了多少分 —— 不用点进去才知道。
 */
@Data
@Accessors(chain = true)
@ApiModel("课程试卷（学生视角）")
public class StudentCourseExamVO {

    private Long examId;
    private String name;
    private Long courseId;
    private String courseName;
    @ApiModelProperty("1 正式考试 / 2 随堂练习（P17 起不再产生 2）")
    private Integer examType;
    @ApiModelProperty("考试须知")
    private String notice;
    @ApiModelProperty("题量")
    private Integer questionCount;
    @ApiModelProperty("卷面总分")
    private Integer totalScore;
    @ApiModelProperty("限时（分钟）；0 = 不限时")
    private Integer duration;
    private Integer passScore;
    @ApiModelProperty("我的作答记录id（null = 还没考）")
    private Long myRecordId;
    @ApiModelProperty("我的状态：0 进行中 / 1 已交卷 / 2 已复核；null = 未考")
    private Integer myStatus;
    private Integer myScore;
    private Integer myTotalScore;
    private Integer myPassed;
}
