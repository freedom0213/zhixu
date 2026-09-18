package com.zhixu.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 题目被试卷引用的情况（契约 §4.2 GET /questions/{id}/usage，停用前提示用）
 * </p>
 */
@Data
@ApiModel(description = "题目引用情况")
public class QuestionUsageVO {

    @ApiModelProperty("引用该题的考试数量")
    private Integer usageCount;

    @ApiModelProperty("引用该题的考试列表")
    private List<Map<String, Object>> papers;
}
