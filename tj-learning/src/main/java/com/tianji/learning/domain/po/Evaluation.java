package com.tianji.learning.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@TableName("evaluation")
public class Evaluation implements Serializable {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long courseId;
    private Long userId;
    private Long teacherId;
    private Integer contentRating;
    private Integer teachingRating;
    private Integer difficultyRating;
    private Integer valueRating;
    private BigDecimal overallRating;
    private String comment;
    private Boolean anonymity;
    private Integer helpCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
