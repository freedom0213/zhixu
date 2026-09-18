package com.zhixu.exam.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * <p>
 * 题目批量调整（契约 §4.2 PUT /questions/batch）
 * 硬规则：**只对自己出的题生效**，别人的题跳过（skipped），返回 changed / skipped 计数。
 * </p>
 */
@Data
@ApiModel(description = "题目批量调整参数")
public class BatchPatchDTO {

    @ApiModelProperty("要调整的题目id集合")
    private List<Long> ids;

    @ApiModelProperty(value = "难度，1：简单，2：中等，3：困难（选填）")
    private Integer difficulty;

    @ApiModelProperty(value = "状态，1：可用，0：已停用（选填）")
    private Integer status;
}
