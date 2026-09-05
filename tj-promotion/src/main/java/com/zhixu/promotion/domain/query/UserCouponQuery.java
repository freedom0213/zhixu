package com.zhixu.promotion.domain.query;

import com.zhixu.common.domain.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserCouponQuery extends PageQuery {
    private Integer status;
}
