package com.zhixu.learning.service;

import com.zhixu.learning.domain.vo.LearningDurationSummaryVO;

/** 学习时长（P27） */
public interface ILearningDurationService {

    /**
     * 当前登录用户的学习时长汇总。
     * @param days 窗口天数（热力图 140 天 = 20 周；默认 140，上限 400）
     */
    LearningDurationSummaryVO summary(Integer days);
}
