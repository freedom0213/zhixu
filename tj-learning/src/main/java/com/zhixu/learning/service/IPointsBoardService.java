package com.zhixu.learning.service;

import com.zhixu.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixu.learning.domain.query.PointsBoardQuery;
import com.zhixu.learning.domain.vo.PointsBoardVO;

import java.util.List;

/**
 * <p>
 * 学霸天梯榜 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-14
 */
public interface IPointsBoardService extends IService<PointsBoard> {

    PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query);

    void createPointsBoardTableBySeason(Integer season);

    List<PointsBoard> queryCurrentBoardList(String key, Integer pageNo, Integer pageSize);

}
