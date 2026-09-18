package com.zhixu.learning.service.impl;

import com.zhixu.common.utils.UserContext;
import com.zhixu.learning.domain.vo.LearningDurationDayVO;
import com.zhixu.learning.domain.vo.LearningDurationSummaryVO;
import com.zhixu.learning.mapper.LearningDurationMapper;
import com.zhixu.learning.service.ILearningDurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 学习时长查询（P27）：汇总数字**一律从按天明细推导**，不另算一套 */
@Service
@RequiredArgsConstructor
public class LearningDurationServiceImpl implements ILearningDurationService {

    private final LearningDurationMapper durationMapper;

    @Override
    public LearningDurationSummaryVO summary(Integer days) {
        Long userId = UserContext.getUser();
        int window = (days == null || days <= 0) ? 140 : Math.min(days, 400);
        LocalDate today = LocalDate.now();

        List<LearningDurationDayVO> daily = durationMapper.selectDailySince(userId, today.minusDays(window - 1L));
        if (daily == null) {
            daily = new ArrayList<>();
        }
        int windowSec = 0;
        int todaySec = 0;
        for (LearningDurationDayVO day : daily) {
            int sec = day.getDurationSec() == null ? 0 : day.getDurationSec();
            windowSec += sec;
            if (today.equals(day.getLearnDate())) {
                todaySec = sec;
            }
        }
        Integer total = durationMapper.sumAll(userId);
        LearningDurationSummaryVO vo = new LearningDurationSummaryVO();
        vo.setTotalSec(total == null ? 0 : total);
        vo.setTodaySec(todaySec);
        vo.setWindowSec(windowSec);
        vo.setActiveDays(daily.size());                                  // 有学习的天数 = 明细行数
        vo.setAvgDailySec(daily.isEmpty() ? 0 : windowSec / daily.size()); // 按有学习的天数平均
        vo.setDays(daily);
        return vo;
    }
}
