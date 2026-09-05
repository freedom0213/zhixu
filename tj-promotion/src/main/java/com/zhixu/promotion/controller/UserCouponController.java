package com.zhixu.promotion.controller;


import com.zhixu.promotion.service.IUserCouponService;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.promotion.domain.query.UserCouponQuery;
import com.zhixu.promotion.domain.vo.UserCouponVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.RestController;
import java.util.*;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-21
 */
@RestController
@RequestMapping("/user-coupons")
@Api(tags = "用户优惠劵相关接口")
@RequiredArgsConstructor
public class UserCouponController {

    private final IUserCouponService userCouponService;

    @PostMapping("/{id}/receive")
    @ApiOperation("用户领取优惠劵")
    public void receiveCoupon(@PathVariable("id") Long couponId){
        userCouponService.receiveCoupon(couponId);
    }

    @PostMapping("/{code}/exchange")
    @ApiOperation("兑换码兑换优惠劵")
    public void exchangeCoupon(@PathVariable("code") String code){
        userCouponService.exchangeCoupon(code);
    }

    @GetMapping("/page")
    @ApiOperation("查询我的优惠券")
    public PageDTO<UserCouponVO> queryMyCoupons(UserCouponQuery query) {
        return userCouponService.queryMyCoupons(query);
    }

    @GetMapping("/available")
    public List<Map<String, Object>> queryAvailableCoupons(@RequestParam Integer amount) {
        return userCouponService.queryAvailableCoupons(amount);
    }


}
