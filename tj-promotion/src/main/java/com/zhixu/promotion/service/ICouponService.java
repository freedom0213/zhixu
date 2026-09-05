package com.zhixu.promotion.service;

import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.promotion.domain.dto.CouponFormDTO;
import com.zhixu.promotion.domain.dto.CouponIssueFormDTO;
import com.zhixu.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixu.promotion.domain.query.CouponQuery;
import com.zhixu.promotion.domain.vo.CouponPageVO;
import com.zhixu.promotion.domain.vo.CouponVO;

import javax.validation.Valid;
import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-20
 */
public interface ICouponService extends IService<Coupon> {

    void saveCoupon(@Valid CouponFormDTO cfDTO);

    PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query);

    void beginIssue(@Valid CouponIssueFormDTO dto);

    void pauseIssue(Long id);

    List<CouponVO> queryIssuingCoupons();


}
