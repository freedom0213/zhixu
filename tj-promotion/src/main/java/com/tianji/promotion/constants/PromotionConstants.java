package com.tianji.promotion.constants;

public interface PromotionConstants {
    //redis中优惠劵的自增key
    String COUPON_CODE_SERIAL_KEY = "coupon:code:serial";
    //redis中兑换码key
    String COUPON_RANGE_KEY = "coupon:code:range";
    //redis中bitMap记录兑换码兑换情况key
    String COUPON_CODE_MAP_KEY = "coupon:code:map";
    //优惠劵缓存id
    String COUPON_CACHE_KEY_PREFIX = "prs:coupon:";
    String USER_COUPON_CACHE_KEY_PREFIX = "prs_user_coupon:";
}
