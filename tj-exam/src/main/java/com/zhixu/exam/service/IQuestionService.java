package com.zhixu.exam.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixu.api.dto.exam.QuestionDTO;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.exam.domain.dto.BatchPatchDTO;
import com.zhixu.exam.domain.dto.QuestionFormDTO;
import com.zhixu.exam.domain.po.Question;
import com.zhixu.exam.domain.query.QuestionPageQuery;
import com.zhixu.exam.domain.vo.IdentityVO;
import com.zhixu.exam.domain.vo.QuestionDetailVO;
import com.zhixu.exam.domain.vo.QuestionPageVO;
import com.zhixu.exam.domain.vo.QuestionVisibilityVO;
import com.zhixu.exam.domain.vo.QuestionUsageVO;

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

    // ---- P11 教师端新增（契约 §4.2）----

    /**
     * 当前教师身份（决定「全部题目」分段与出题人列是否出现）
     */
    IdentityVO getIdentity();

    /**
     * 停用 / 启用（停用不删除；只有出题人本人可操作）
     */
    void updateStatus(Long id, Integer status);

    /**
     * 批量调整难度 / 状态 —— 只对自己出的题生效，返回 changed / skipped
     */
    Map<String, Integer> batchPatch(BatchPatchDTO dto);

    /**
     * 被考试引用的情况（停用前提示用）
     */
    QuestionUsageVO queryUsage(Long id);

    PageDTO<QuestionPageVO> queryQuestionByPage(QuestionPageQuery query);

    /**
     * 设置题目的可见范围（P17）：0 私有 / 1 公开。
     * 仅作者可改；**撤回（1→0）时若已被试卷引用会被拒绝** ——
     * 别人的卷子里会突然少一道题，先处理引用再撤（或改用「停用」）。
     */
    QuestionVisibilityVO setVisibility(Long id, Integer visibility);

    QuestionDetailVO queryQuestionDetailById(Long id);

    List<QuestionDTO> queryQuestionByIds(List<Long> ids);

    Map<Long, Integer> countQuestionNumOfCreater(List<Long> createrIds);

    List<QuestionDTO> queryQuestionByBizId(Long bizId);

    Boolean checkNameValid(String name);

    Map<Long, Integer> queryQuestionScores(List<Long> ids);
}
