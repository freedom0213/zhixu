package com.zhixu.learning.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PointsExchangeRecordVO {
    private Long id;
    private Long itemId;
    private String itemName;
    private Integer pointsUsed;
    private Integer status;
    private String address;
    private String phone;
    private LocalDateTime createTime;
}
