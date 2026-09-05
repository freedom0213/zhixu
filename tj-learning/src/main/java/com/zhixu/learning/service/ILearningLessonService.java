package com.zhixu.learning.service;

import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.common.domain.query.PageQuery;
import com.zhixu.learning.domain.dto.LearningPlanDTO;
import com.zhixu.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixu.learning.domain.vo.LearningLessonVO;
import com.zhixu.learning.domain.vo.LearningPlanPageVO;

import javax.validation.Valid;
import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-04
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    void addUserLessons(Long userId, List<Long> courseIds);

    PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery);

    LearningLessonVO queryMyCurrentLesson();

    void deleteCourseFromLesson(Long userId, Long courseId);

    Long isLessonValid(Long courseId);

    LearningLessonVO queryLessonByCourseId(Long courseId);

    Integer countLearningLessonByCourseId(Long courseId);

    LearningLesson queryLessonByCourseIdAndUserId(Long courseId, Long userId);

    void createLearningPlans(@Valid LearningPlanDTO planDTO,Integer freq);

    LearningPlanPageVO queryMyPlans(PageQuery pageQuery);


}
