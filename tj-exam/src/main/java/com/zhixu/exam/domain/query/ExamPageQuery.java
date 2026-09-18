package com.zhixu.exam.domain.query;

import com.zhixu.common.domain.query.PageQuery;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@ApiModel(description = "考试分页查询条件")
public class ExamPageQuery extends PageQuery {

    @ApiModelProperty("状态，0：草稿，1：已发布，2：批改中，3：已结束")
    private Integer status;

    @ApiModelProperty("名称 / 课程名关键字")
    private String keyword;

    @ApiModelProperty("只看我创建的（教师端考试管理传 1；不传=不过滤，老接口行为不变）")
    private Boolean mine;
}
