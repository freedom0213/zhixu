package com.zhixu.promotion.domain.vo;

import com.zhixu.promotion.enums.DiscountType;
import com.zhixu.promotion.enums.UserCouponStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserCouponVO {
    private Long id;
    private Long couponId;
    private String name;
    private Boolean specific;
    private DiscountType discountType;
    private Integer thresholdAmount;
    private Integer discountValue;
    private Integer maxDiscountAmount;
    private Integer termDays;
    private LocalDateTime termBeginTime;
    private LocalDateTime termEndTime;
    private UserCouponStatus status;
    private LocalDateTime usedTime;
    private LocalDateTime createTime;
}
