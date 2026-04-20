package com.tianji.learning.controller;


import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.service.IPointsBoardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学霸天梯榜 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-14
 */
@RestController
@RequestMapping("/boards")
@Api(tags = "学霸天梯榜相关接口")
@RequiredArgsConstructor
public class PointsBoardController {

    private final IPointsBoardService boardService;

    @GetMapping
    @ApiOperation("根据赛季查询积分排行榜以及当前用户的积分和排名信息")
    public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query) {
        return boardService.queryPointsBoardBySeason(query);
    }



}
