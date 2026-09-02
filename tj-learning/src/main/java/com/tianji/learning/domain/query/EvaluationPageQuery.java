package com.tianji.learning.domain.query;

import com.tianji.common.domain.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EvaluationPageQuery extends PageQuery {
    private Long courseId;
    private Long teacherId;
    private Boolean onlyMine = false;
}
