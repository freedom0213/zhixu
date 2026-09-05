package com.zhixu.promotion.controller;


import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.promotion.domain.dto.CouponFormDTO;
import com.zhixu.promotion.domain.dto.CouponIssueFormDTO;
import com.zhixu.promotion.domain.query.CouponQuery;
import com.zhixu.promotion.domain.vo.CouponPageVO;
import com.zhixu.promotion.domain.vo.CouponVO;
import com.zhixu.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-20
 */
@RestController
@RequestMapping("/coupons")
@Api(tags = "优惠卷相关接口")
@RequiredArgsConstructor
public class CouponController {

    private final ICouponService couponService;

    @ApiOperation("新增优惠卷")
    @PostMapping
    public void saveCoupon(@RequestBody @Valid CouponFormDTO cfDTO){
        couponService.saveCoupon(cfDTO);
    }

    @ApiOperation("分页查询优惠卷")
    @GetMapping("/page")
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query){
       return couponService.queryCouponByPage(query);
    }

    @ApiOperation("优惠劵发放")
    @PutMapping("/{id}/issue")
    public void beginIssue(@RequestBody @Valid CouponIssueFormDTO dto){
        couponService.beginIssue(dto);
    }

    @ApiOperation("查询发放中的优惠劵")
    @GetMapping("/list")
    public List<CouponVO> queryIssuingCoupons(){
       return couponService.queryIssuingCoupons();
    }



}
