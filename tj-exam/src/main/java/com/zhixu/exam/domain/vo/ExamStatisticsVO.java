package com.zhixu.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 一场考试的成绩统计（p14 / p15）。
 * 口径：**只计已交卷**的学生；及格线取 exam.pass_score（不写死 60）。
 * 分数段按「得分率」划分（0-59 / 60-69 / 70-79 / 80-89 / 90-100），
 * 这样不同总分的卷子可以横向比较。
 *
 * ⚠️ 比率字段统一是**百分比（0~100，保留一位小数）**，不是 0~1 —— 前端展示前要 /100 或加 %。
 * 没人答过 / 没人交卷时为 **null**，前端显示「—」（不许拿 0 冒充）。
 */
@Data
@ApiModel("考试成绩统计")
public class ExamStatisticsVO {
    private Long examId;
    private String examName;
    private Integer totalScore;
    private Integer passScore;
    @ApiModelProperty("已交卷人数")
    private Integer submittedCount;
    @ApiModelProperty("平均分（保留一位小数）")
    private Double avgScore;
    private Integer maxScore;
    private Integer minScore;
    @ApiModelProperty("通过率（百分比，保留一位小数）")
    private Double passRate;
    private List<Bucket> distribution = new ArrayList<>();

    @ApiModelProperty("逐题统计：题面取当前快照，答题数/答对数来自作答明细（只计已交卷）")
    private List<QuestionStat> perQuestion = new ArrayList<>();

    @Data
    @Accessors(chain = true)
    public static class QuestionStat {
        @ApiModelProperty("题号（按快照顺序，从 1 开始）")
        private Integer order;
        private Long questionId;
        @ApiModelProperty("题干（来自快照）")
        private String stem;
        private Integer type;
        private Integer score;
        @ApiModelProperty("答题人数")
        private Integer attempts;
        @ApiModelProperty("答对人数")
        private Integer correctCount;
        @ApiModelProperty("正确率（百分比，保留一位小数）；没人答过为 null")
        private Double correctRate;
    }

    @Data
    @Accessors(chain = true)
    public static class Bucket {
        @ApiModelProperty("分数段标签，如 60-69")
        private String label;
        private Integer count;
    }
}
