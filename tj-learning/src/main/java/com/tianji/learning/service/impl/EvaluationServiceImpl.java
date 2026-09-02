package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.EvaluationFormDTO;
import com.tianji.learning.domain.po.Evaluation;
import com.tianji.learning.domain.query.EvaluationPageQuery;
import com.tianji.learning.domain.vo.EvaluationVO;
import com.tianji.learning.mapper.EvaluationMapper;
import com.tianji.learning.service.IEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl extends ServiceImpl<EvaluationMapper, Evaluation> implements IEvaluationService {
    private final UserClient userClient;

    @Override
    public PageDTO<EvaluationVO> queryPage(EvaluationPageQuery query) {
        Long userId = UserContext.getUser();
        Page<Evaluation> page = lambdaQuery()
                .eq(query.getCourseId() != null, Evaluation::getCourseId, query.getCourseId())
                .eq(query.getTeacherId() != null, Evaluation::getTeacherId, query.getTeacherId())
                .eq(Boolean.TRUE.equals(query.getOnlyMine()), Evaluation::getUserId, userId)
                .orderByDesc(Evaluation::getCreateTime)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        if (CollUtils.isEmpty(page.getRecords())) {
            return PageDTO.empty(page);
        }
        return PageDTO.of(page, toVOs(page.getRecords()));
    }

    @Override
    public EvaluationVO queryById(Long id) {
        Evaluation evaluation = getById(id);
        if (evaluation == null) {
            throw new BizIllegalException("评价不存在");
        }
        return toVOs(Collections.singletonList(evaluation)).get(0);
    }

    @Override
    @Transactional
    public void saveEvaluation(EvaluationFormDTO form) {
        Long userId = UserContext.getUser();
        if (lambdaQuery().eq(Evaluation::getUserId, userId).eq(Evaluation::getCourseId, form.getCourseId()).count() > 0) {
            throw new BizIllegalException("同一门课程只能评价一次");
        }
        Evaluation evaluation = build(form, userId);
        save(evaluation);
    }

    @Override
    @Transactional
    public void updateEvaluation(Long id, EvaluationFormDTO form) {
        Evaluation evaluation = getById(id);
        assertOwner(evaluation);
        Evaluation update = build(form, UserContext.getUser());
        update.setId(id);
        updateById(update);
    }

    @Override
    public void deleteEvaluation(Long id) {
        Evaluation evaluation = getById(id);
        assertOwner(evaluation);
        removeById(id);
    }

    @Override
    public boolean evaluated(Long courseId) {
        return lambdaQuery().eq(Evaluation::getUserId, UserContext.getUser()).eq(Evaluation::getCourseId, courseId).count() > 0;
    }

    private Evaluation build(EvaluationFormDTO form, Long userId) {
        BigDecimal overall = BigDecimal.valueOf(form.getContentRating() + form.getTeachingRating()
                        + form.getDifficultyRating() + form.getValueRating())
                .divide(BigDecimal.valueOf(4), 1, RoundingMode.HALF_UP);
        return new Evaluation().setCourseId(form.getCourseId()).setUserId(userId).setTeacherId(form.getTeacherId())
                .setContentRating(form.getContentRating()).setTeachingRating(form.getTeachingRating())
                .setDifficultyRating(form.getDifficultyRating()).setValueRating(form.getValueRating())
                .setOverallRating(overall).setComment(form.getComment()).setAnonymity(form.getAnonymity())
                .setHelpCount(0);
    }

    private void assertOwner(Evaluation evaluation) {
        if (evaluation == null || !Objects.equals(evaluation.getUserId(), UserContext.getUser())) {
            throw new BizIllegalException("无权操作他人评价");
        }
    }

    private List<EvaluationVO> toVOs(List<Evaluation> evaluations) {
        Set<Long> userIds = evaluations.stream().filter(e -> !Boolean.TRUE.equals(e.getAnonymity()))
                .map(Evaluation::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        evaluations.stream().map(Evaluation::getTeacherId).filter(Objects::nonNull).forEach(userIds::add);
        Map<Long, UserDTO> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            if (CollUtils.isNotEmpty(users)) {
                userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
            }
        }
        Map<Long, UserDTO> finalUserMap = userMap;
        return evaluations.stream().map(e -> {
            EvaluationVO vo = BeanUtils.toBean(e, EvaluationVO.class);
            if (Boolean.TRUE.equals(e.getAnonymity())) {
                vo.setUserId(null);
                vo.setUserName("匿名用户");
                vo.setUserIcon(null);
            } else {
                UserDTO user = finalUserMap.get(e.getUserId());
                if (user != null) {
                    vo.setUserName(user.getName());
                    vo.setUserIcon(user.getIcon());
                }
            }
            UserDTO teacher = finalUserMap.get(e.getTeacherId());
            if (teacher != null) {
                vo.setTeacherName(teacher.getName());
            }
            vo.setIsHelpful(false);
            vo.setHelpCount(e.getHelpCount() == null ? 0 : e.getHelpCount());
            return vo;
        }).collect(Collectors.toList());
    }
}
