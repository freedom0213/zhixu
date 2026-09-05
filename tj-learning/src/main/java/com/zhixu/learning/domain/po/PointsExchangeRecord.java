package com.zhixu.learning.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("points_exchange_record")
public class PointsExchangeRecord {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long itemId;
    private Integer pointsUsed;
    private Integer status;
    private String address;
    private String phone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
