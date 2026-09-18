package com.zhixu.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * <p>
 * 当前教师身份（契约 §4.2 GET /questions/identity）
 * </p>
 */
@Data
@ApiModel(description = "教师身份")
public class IdentityVO {

    @ApiModelProperty("用户id")
    private Long id;

    @ApiModelProperty("用户名")
    private String name;

    @ApiModelProperty("是否已绑定学校（决定「全部题目」分段与出题人列是否出现）")
    private Boolean boundSchool;
}
