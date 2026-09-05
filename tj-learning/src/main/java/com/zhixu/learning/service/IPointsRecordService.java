package com.zhixu.learning.service;

import com.zhixu.learning.domain.enums.PointsRecordType;
import com.zhixu.learning.domain.po.PointsRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixu.learning.domain.vo.PointsStatisticsVO;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-14
 */
public interface IPointsRecordService extends IService<PointsRecord> {

    void addPointsRecord(Long userId, int i, PointsRecordType pointsRecordType);

    List<PointsStatisticsVO> queryMyPointsToday();

    int queryMyTotalPoints();

}
