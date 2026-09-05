package com.zhixu.exam.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixu.api.dto.exam.QuestionDTO;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.exam.domain.dto.QuestionFormDTO;
import com.zhixu.exam.domain.po.Question;
import com.zhixu.exam.domain.query.QuestionPageQuery;
import com.zhixu.exam.domain.vo.QuestionDetailVO;
import com.zhixu.exam.domain.vo.QuestionPageVO;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 题目 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-02
 */
public interface IQuestionService extends IService<Question> {

    void addQuestion(QuestionFormDTO questionFormDTO);

    void updateQuestion(QuestionFormDTO questionDTO);

    void deleteQuestionById(Long id);

    PageDTO<QuestionPageVO> queryQuestionByPage(QuestionPageQuery query);

    QuestionDetailVO queryQuestionDetailById(Long id);

    List<QuestionDTO> queryQuestionByIds(List<Long> ids);

    Map<Long, Integer> countQuestionNumOfCreater(List<Long> createrIds);

    List<QuestionDTO> queryQuestionByBizId(Long bizId);

    Boolean checkNameValid(String name);

    Map<Long, Integer> queryQuestionScores(List<Long> ids);
}
