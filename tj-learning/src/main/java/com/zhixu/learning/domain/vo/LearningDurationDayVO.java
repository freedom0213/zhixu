package com.zhixu.learning.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDate;

/** 某一天的学习时长（P27 热力图的一个格子） */
@Data
@ApiModel("每日学习时长")
public class LearningDurationDayVO {

    @ApiModelProperty("日期 yyyy-MM-dd")
    private LocalDate learnDate;

    @ApiModelProperty("当天学习时长（秒）")
    private Integer durationSec;
}
