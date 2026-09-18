package com.zhixu.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 题目可见范围（P17）。
 * refCount 是**真实引用数**（查 exam.items 的 JSON_CONTAINS），撤回前用它判断能不能撤。
 */
@Data
@Accessors(chain = true)
@ApiModel("题目可见范围")
public class QuestionVisibilityVO {

    private Long id;
    @ApiModelProperty("0 私有（仅我）/ 1 公开（平台可见）")
    private Integer visibility;
    @ApiModelProperty("公开发布时间（私有时为 null）")
    private LocalDateTime publishTime;
    @ApiModelProperty("被多少份试卷引用（真实数）")
    private Integer refCount;
}
