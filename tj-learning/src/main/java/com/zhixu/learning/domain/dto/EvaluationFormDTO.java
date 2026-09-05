package com.zhixu.learning.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
@ApiModel(description = "课程评价")
public class EvaluationFormDTO {
    private Long id;

    @NotNull(message = "课程不能为空")
    private Long courseId;

    private Long teacherId;

    @NotNull @Min(1) @Max(5)
    @ApiModelProperty("内容评分 1-5")
    private Integer contentRating;

    @NotNull @Min(1) @Max(5)
    @ApiModelProperty("教学评分 1-5")
    private Integer teachingRating;

    @NotNull @Min(1) @Max(5)
    @ApiModelProperty("难度评分 1-5")
    private Integer difficultyRating;

    @NotNull @Min(1) @Max(5)
    @ApiModelProperty("价值评分 1-5")
    private Integer valueRating;

    @NotNull
    @Size(min = 10, max = 1000, message = "评价内容需要 10-1000 个字")
    private String comment;

    @NotNull
    private Boolean anonymity = false;
}
