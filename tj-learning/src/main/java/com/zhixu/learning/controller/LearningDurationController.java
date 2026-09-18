package com.zhixu.learning.controller;

import com.zhixu.learning.domain.vo.LearningDurationSummaryVO;
import com.zhixu.learning.service.ILearningDurationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 学习时长（P27）：学生端「学习足迹」热力图与累计/日均学习的唯一数据源 */
@RestController
@RequestMapping("/learning-durations")
@Api(tags = "学习时长相关接口")
@RequiredArgsConstructor
public class LearningDurationController {

    private final ILearningDurationService durationService;

    @GetMapping("/summary")
    @ApiOperation("我的学习时长汇总（含按天明细，供热力图）")
    public LearningDurationSummaryVO summary(
            @RequestParam(value = "days", required = false, defaultValue = "140") Integer days) {
        return durationService.summary(days);
    }
}
