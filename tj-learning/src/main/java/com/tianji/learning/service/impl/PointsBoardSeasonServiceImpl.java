package com.tianji.learning.service.impl;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.mapper.PointsBoardSeasonMapper;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-14
 */
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason> implements IPointsBoardSeasonService {

    /**
     * 根据传过来的时间获取赛季
     * @param time 时间
     * @return 返回的是赛季id
     */
    @Override
    public Integer querySeasonByTime(LocalDateTime time) {
        //其实数据库中共有赛季表 这个表会记录赛季id和赛季持续时间 只需要比较即可
        Optional<PointsBoardSeason> optional  = lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .oneOpt();

        return optional.map(PointsBoardSeason::getId).orElse(null);


    }
}
