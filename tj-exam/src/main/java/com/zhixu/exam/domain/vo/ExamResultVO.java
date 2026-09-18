package com.zhixu.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 交卷结果 / 答卷回看（p14）。
 * 读的是**作答时冻结的那份快照**，所以之后讲师改题库、重新发布，这里都不会变。
 */
@Data
@ApiModel("作答结果")
public class ExamResultVO {

    private Long recordId;
    private Long examId;
    @ApiModelProperty("作答学生id（讲师看答卷时用；学生看自己的答卷也会带）")
    private Long studentId;
    @ApiModelProperty("作答学生姓名（后端查不到就为空，前端显示「学员」）")
    private String studentName;
    private String examName;
    private String courseName;
    @ApiModelProperty("得分")
    private Integer score;
    @ApiModelProperty("卷面总分")
    private Integer totalScore;
    private Integer correctCount;
    private Integer totalCount;
    @ApiModelProperty("是否及格")
    private Integer passed;
    private Integer passScore;
    @ApiModelProperty("0：进行中，1：已交卷，2：已复核")
    private Integer status;
    @ApiModelProperty("作答用时（秒）")
    private Long duration;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    private List<Detail> details = new ArrayList<>();

    @Data
    public static class Detail {
        @ApiModelProperty("题号（从 1 开始）")
        private Integer index;
        private Long questionId;
        private Integer type;
        private String name;
        private List<String> options;
        @ApiModelProperty("我的答案")
        private String myAnswer;
        @ApiModelProperty("标准答案")
        private String correctAnswer;
        private String analysis;
        private Integer correct;
        private Integer score;
        private Integer maxScore;
    }
}
