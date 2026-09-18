package com.zhixu.learning.domain.vo;

import com.zhixu.learning.domain.enums.LessonStatus;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程学生明细（讲师视角 · P24 学生分析页）
 * <p>
 * ⚠️ 只放服务端**真实拿得到**的字段：学习时长在 learning_lesson 里没有对应列，
 * 因此这里不提供 —— 前端该位置显示「—」，不要用 0 或估算值充数。
 */
@Data
@ApiModel("课程学生明细（讲师视角）")
public class CourseStudentVO {

    @ApiModelProperty("学生用户id")
    private Long userId;

    @ApiModelProperty("学生昵称（user_detail.name，可能为空）")
    private String name;

    @ApiModelProperty("学生账号（手机号）")
    private String cellPhone;

    @ApiModelProperty("学生头像")
    private String icon;

    @ApiModelProperty("报名时间（learning_lesson.create_time）")
    private LocalDateTime joinTime;

    @ApiModelProperty("已学小节数")
    private Integer learnedSections;

    @ApiModelProperty("最近学习时间；为空表示报名后从未学习")
    private LocalDateTime latestLearnTime;

    @ApiModelProperty("学习状态 0未学习/1学习中/2已学完/3已过期")
    private LessonStatus status;
}
