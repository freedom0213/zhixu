package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-04
 */
@RestController
@RequestMapping("/lessons")
@Api(tags="我的课表相关接口")
@RequiredArgsConstructor
public class LearningLessonController {

    public final ILearningLessonService iLearningLessonService;

    @GetMapping("/page")
    @ApiOperation("分页查询我的课表")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        return iLearningLessonService.queryMyLessons(pageQuery);
    }

    @GetMapping("/now")
    @ApiOperation("查询我正在学习的课程")
    public LearningLessonVO queryMyCurrentLesson(){
        return iLearningLessonService.queryMyCurrentLesson();
    }

    @DeleteMapping("/{courseId}")
    @ApiOperation("用户手动删除当前课程")
    public void deleteCourseFromLesson(@PathVariable("courseId") Long courseId) {
        Long userId = UserContext.getUser();
        iLearningLessonService.deleteCourseFromLesson(userId,courseId);
    }

    @GetMapping("/{courseId}/valid")
    @ApiOperation("检验课程是否有效")
    public Long isLessonValid(@PathVariable("courseId") Long courseId) {
        return iLearningLessonService.isLessonValid(courseId);
    }

    @GetMapping("/{courseId}")
    @ApiOperation("查询用户是否含有该课程并返回学习状态")
    public LearningLessonVO queryLessonByCourseId(@PathVariable("courseId") Long courseId) {
        return iLearningLessonService.queryLessonByCourseId(courseId);
    }

    @GetMapping("/{courseId}/count")
    @ApiOperation("查询课程的报名人数")
    public Integer countLearningLessonByCourseId(@PathVariable("courseId")Long  courseId) {
        return iLearningLessonService.countLearningLessonByCourseId(courseId);
    }


}
