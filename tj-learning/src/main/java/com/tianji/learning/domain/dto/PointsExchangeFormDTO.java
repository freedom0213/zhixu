package com.tianji.learning.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class PointsExchangeFormDTO {
    @NotNull
    private Long itemId;
    @Size(max = 255)
    private String address;
    @Size(max = 30)
    private String phone;
}
