package com.zhixu.learning.controller;


import com.zhixu.api.dto.leanring.LearningLessonDTO;
import com.zhixu.learning.domain.dto.LearningRecordFormDTO;
import com.zhixu.learning.service.ILearningRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学习记录表 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-06
 */
@RestController
@RequestMapping("/learning-records")
@Api(tags = "学习记录相关接口")
@RequiredArgsConstructor
public class LearningRecordController {

    public final ILearningRecordService recordService;

    //查询用户指定课程的学习进度，实际上不仅需要查询学习进度，还要查询课程基本信息，所以这个方法实际是让其它服务利用Client远程调用的
    @ApiOperation("查询当前用户指定课程的学习进度")
    @GetMapping("/course/{courseId}")
    public LearningLessonDTO queryLearningRecordByCourse(
            @ApiParam(value = "课程id", example = "2")
            @PathVariable("courseId")Long courseId ) {
        return recordService.queryLearningRecordByCourse(courseId);
    }
    @ApiOperation("提交学习记录")
    @PostMapping
    // 🔴 P25：必须 @RequestBody —— 前端（老学习页与新学员端）都是用 JSON body 发的，
    //    原来按 form 绑定 → body 里 lessonId/sectionId/moment/duration 全部收不到，
    //    写库时 lesson_id 为 null 直接 500 → **学习记录一条都没落过，完成状态从来没生效**。
    public void addLearningRecord(@RequestBody LearningRecordFormDTO recordFormDTO) {
        recordService.addLearningRecord(recordFormDTO);
    }


}
