package com.zhixu.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 开始作答（p14）：返回这场卷子的题目。
 * ⚠️ **不含标准答案与解析** —— 交卷后才由 `/result` 给出，避免前端直接从网络面板抄答案。
 */
@Data
@ApiModel("开始作答")
public class ExamStartVO {

    @ApiModelProperty("本次作答记录id")
    private Long recordId;
    @ApiModelProperty("考试/练习id")
    private Long examId;
    @ApiModelProperty("卷子名称")
    private String name;
    @ApiModelProperty("类型，1：正式考试，2：随堂练习")
    private Integer examType;
    @ApiModelProperty("时长（分钟）")
    private Integer duration;
    @ApiModelProperty("及格线")
    private Integer passScore;
    @ApiModelProperty("考试须知")
    private String notice;
    @ApiModelProperty("卷面总分")
    private Integer totalScore;
    @ApiModelProperty("题目（无答案）")
    private List<Question> questions = new ArrayList<>();

    @Data
    public static class Question {
        @ApiModelProperty("题目id")
        private Long id;
        @ApiModelProperty("题型，1：单选，2：多选")
        private Integer type;
        @ApiModelProperty("题干")
        private String name;
        @ApiModelProperty("选项（按 A/B/C… 顺序）")
        private List<String> options;
        @ApiModelProperty("本题分值")
        private Integer score;
    }
}
