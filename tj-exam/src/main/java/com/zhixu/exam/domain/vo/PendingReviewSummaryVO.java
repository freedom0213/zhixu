package com.zhixu.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 待批改总览（P18）：`items` 是各场考试的明细，三个总数**从明细求和**得出 ——
 * 页面顶部的大数字与列表永远一致，不会一个 5 一个 3。
 */
@Data
@Accessors(chain = true)
@ApiModel("待批改总览")
public class PendingReviewSummaryVO {

    @ApiModelProperty("涉及多少场考试（= items.size()）")
    private Integer examCount;
    @ApiModelProperty("待复核份数合计")
    private Integer pendingCount;
    @ApiModelProperty("已交卷份数合计")
    private Integer submittedCount;
    private List<PendingReviewItemVO> items;
}
