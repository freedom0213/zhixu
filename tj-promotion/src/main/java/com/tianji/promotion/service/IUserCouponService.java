package com.tianji.promotion.service;

import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.vo.UserCouponVO;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-21
 */
public interface IUserCouponService extends IService<UserCoupon> {

    void receiveCoupon(Long couponId);

    void exchangeCoupon(String code);

    void checkAndCreateUserCoupon(UserCouponDTO uc);

    PageDTO<UserCouponVO> queryMyCoupons(UserCouponQuery query);

    List<Map<String, Object>> queryAvailableCoupons(Integer amount);
}
