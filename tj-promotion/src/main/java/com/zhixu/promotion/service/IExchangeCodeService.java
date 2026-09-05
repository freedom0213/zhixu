package com.zhixu.promotion.service;

import com.zhixu.promotion.domain.po.Coupon;
import com.zhixu.promotion.domain.po.ExchangeCode;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-20
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {
    void asyncGenerateCode(Coupon coupon);

    boolean updateExchangeMark(long serialNum, boolean b);

    Long exchangeTargetId(long serialNum);

}

