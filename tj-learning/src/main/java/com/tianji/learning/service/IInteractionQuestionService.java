package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import javassist.NotFoundException;

/**
 * <p>
 * 互动提问的问题表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-10
 */
public interface IInteractionQuestionService extends IService<InteractionQuestion> {

    void saveQuestion(QuestionFormDTO questionDTO);

    void updateQuestion(QuestionFormDTO questionDTO,Long id);

    PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query);

    QuestionVO getQuestionById(Long id);

    void deleteQuestionById(Long id) throws NotFoundException;

    PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query);

    void hiddenQuestionAdmin(Integer id, Boolean hidden);

    QuestionAdminVO queryQuestionByIdAdmin(Integer id);
}
