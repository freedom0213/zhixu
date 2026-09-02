package com.tianji.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.EvaluationFormDTO;
import com.tianji.learning.domain.po.Evaluation;
import com.tianji.learning.domain.query.EvaluationPageQuery;
import com.tianji.learning.domain.vo.EvaluationVO;

public interface IEvaluationService extends IService<Evaluation> {
    PageDTO<EvaluationVO> queryPage(EvaluationPageQuery query);
    EvaluationVO queryById(Long id);
    void saveEvaluation(EvaluationFormDTO form);
    void updateEvaluation(Long id, EvaluationFormDTO form);
    void deleteEvaluation(Long id);
    boolean evaluated(Long courseId);
}
