package com.zhixu.course.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 教师端「我的课程」列表项（契约 §18）
 * -----------------------------------------------------------------------------
 * 只放**本站真实存在**的字段：
 *   · 章节数来自目录表（正式表 / 草稿表各查一次）
 *   · 状态由 course.status / course_draft.status 推导（published / draft）
 *   · 封面与价格来自课程本体
 * 学生数、考试场次这类需要跨服务聚合的数字**不在这里编造** —— 前端如实显示「—」，
 * 等对应聚合接口（契约 §12.5）就绪后再补，避免用 0 冒充「没有」。
 */
@Data
@ApiModel("教师端-我的课程")
public class TeacherMyCourseVO {

    private Long id;

    private String name;

    @ApiModelProperty("封面地址")
    private String coverUrl;

    @ApiModelProperty("价格（分）")
    private Integer price;

    @ApiModelProperty("状态：draft / published")
    private String status;

    @ApiModelProperty("章数")
    private Integer chapterNum = 0;

    @ApiModelProperty("小节数")
    private Integer sectionNum = 0;

    @ApiModelProperty("是否本地还有未上架的改动（上架后又编辑过）")
    private Boolean editing = false;
}
