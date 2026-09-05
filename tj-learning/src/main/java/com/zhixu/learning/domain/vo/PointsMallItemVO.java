package com.zhixu.learning.domain.vo;

import lombok.Data;

@Data
public class PointsMallItemVO {
    private Long id;
    private String name;
    private String icon;
    private Integer points;
    private Integer stock;
    private Integer status;
    private String description;
}
