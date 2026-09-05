package com.zhixu.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.learning.domain.dto.EvaluationFormDTO;
import com.zhixu.learning.domain.po.Evaluation;
import com.zhixu.learning.domain.query.EvaluationPageQuery;
import com.zhixu.learning.domain.vo.EvaluationVO;

public interface IEvaluationService extends IService<Evaluation> {
    PageDTO<EvaluationVO> queryPage(EvaluationPageQuery query);
    EvaluationVO queryById(Long id);
    void saveEvaluation(EvaluationFormDTO form);
    void updateEvaluation(Long id, EvaluationFormDTO form);
    void deleteEvaluation(Long id);
    boolean evaluated(Long courseId);
}
