package com.zhixu.learning.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EvaluationVO {
    private Long id;
    private Long courseId;
    private Long userId;
    private String userName;
    private String userIcon;
    private Long teacherId;
    private String teacherName;
    private Integer contentRating;
    private Integer teachingRating;
    private Integer difficultyRating;
    private Integer valueRating;
    private BigDecimal overallRating;
    private String comment;
    private Boolean anonymity;
    private Integer helpCount;
    private Boolean isHelpful = false;
    private LocalDateTime createTime;
}
