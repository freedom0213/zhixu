package com.zhixu.learning.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 学习时长汇总（P27）
 * <p>
 * ⚠️ 口径必须写清楚，否则同一个词会被两种算法解释：
 *  · totalSec    —— **全部时间**的累计（不限窗口）
 *  · windowSec   —— 窗口内合计
 *  · activeDays  —— 窗口内**有学习记录**的天数
 *  · avgDailySec —— windowSec ÷ activeDays（**按有学习的天数平均**，不被空白天拉低）
 */
@Data
@ApiModel("学习时长汇总")
public class LearningDurationSummaryVO {

    @ApiModelProperty("累计学习时长（秒，全部时间）")
    private Integer totalSec;

    @ApiModelProperty("今天的学习时长（秒）")
    private Integer todaySec;

    @ApiModelProperty("窗口内合计（秒）")
    private Integer windowSec;

    @ApiModelProperty("窗口内有学习的天数")
    private Integer activeDays;

    @ApiModelProperty("日均学习时长（秒）= 窗口内合计 ÷ 有学习的天数")
    private Integer avgDailySec;

    @ApiModelProperty("窗口内按天明细（只含有学习的日期）")
    private List<LearningDurationDayVO> days;
}
