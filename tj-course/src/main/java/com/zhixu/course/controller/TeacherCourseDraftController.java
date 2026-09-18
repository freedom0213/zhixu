package com.zhixu.course.controller;

import com.zhixu.course.domain.dto.TeacherCourseDraftDTO;
import com.zhixu.course.domain.vo.TeacherCourseDraftVO;
import com.zhixu.course.domain.vo.TeacherMyCourseVO;
import com.zhixu.course.service.ITeacherCourseDraftService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 教师端建课（契约 §13，走 /cs 前缀）
 * -----------------------------------------------------------------------------
 * 为什么单起一个 controller 而不是改动 CourseController：
 *   CourseController 是**旧管理端**的表单接口（字段与交互按旧后台设计），
 *   教师端向导的领域模型（章-节两层 + 每步校验清单）需要一层稳定契约。
 *   这里只做适配，业务实现全部转调既有 draft service —— 老接口一行不改、老前端不受影响。
 */
@Api(tags = "教师端-建课向导")
@RestController
@RequestMapping("teacher")
@RequiredArgsConstructor
@Validated
public class TeacherCourseDraftController {

    private final ITeacherCourseDraftService teacherCourseDraftService;

    @ApiOperation("读取课程草稿（courseId 为空 = 新建底稿）")
    @GetMapping("course-draft")
    public TeacherCourseDraftVO getDraft(@RequestParam(value = "courseId", required = false) Long courseId) {
        return teacherCourseDraftService.getDraft(courseId);
    }

    @ApiOperation("步骤① 保存基本信息")
    @PutMapping("course-draft/basic")
    public Long saveBasic(@RequestBody @Valid TeacherCourseDraftDTO.BasicSaveDTO dto) {
        return teacherCourseDraftService.saveBasic(dto);
    }

    @ApiOperation("步骤② 保存课程目录（返回含服务端真实 id 的目录）")
    @PutMapping("course-draft/catalog")
    public TeacherCourseDraftVO saveCatalog(@RequestBody @Valid TeacherCourseDraftDTO.CatalogSaveDTO dto) {
        return teacherCourseDraftService.saveCatalog(dto);
    }

    @ApiOperation("步骤③ 小节视频登记")
    @PutMapping("sections/{sid}/video")
    public String saveSectionVideo(@PathVariable("sid") Long sid,
                                   @RequestBody TeacherCourseDraftDTO.SectionVideoDTO dto) {
        return teacherCourseDraftService.saveSectionVideo(sid, dto);
    }

    @ApiOperation("步骤③ 试看开关")
    @PutMapping("sections/{sid}/preview")
    public Boolean togglePreview(@PathVariable("sid") Long sid,
                                 @RequestParam(value = "courseId", required = false) Long courseId,
                                 @RequestBody(required = false) TeacherCourseDraftDTO.SectionVideoDTO dto) {
        Long cid = courseId != null ? courseId : (dto == null ? null : dto.getCourseId());
        return teacherCourseDraftService.toggleSectionPreview(sid, cid);
    }

    @ApiOperation("步骤④ 小节配题（引用题库题目）")
    @PutMapping("sections/{sid}/quiz")
    public void saveSectionQuiz(@PathVariable("sid") Long sid,
                                @RequestBody TeacherCourseDraftDTO.SectionQuizDTO dto) {
        teacherCourseDraftService.saveSectionQuiz(sid, dto);
    }

    @ApiOperation("步骤⑤ 添加讲师")
    @PostMapping("course-draft/teachers")
    public TeacherCourseDraftVO.TeacherItem addTeacher(@RequestBody @Valid TeacherCourseDraftDTO.TeacherSaveDTO dto) {
        return teacherCourseDraftService.addTeacher(dto);
    }

    @ApiOperation("步骤⑤ 移除讲师")
    @DeleteMapping("course-draft/teachers/{tid}")
    public void removeTeacher(@PathVariable("tid") Long tid,
                              @RequestParam("courseId") Long courseId) {
        teacherCourseDraftService.removeTeacher(courseId, tid);
    }

    @ApiOperation("提交上架（基本信息 / 目录不完整 → 422）")
    @PostMapping("course-draft/publish")
    public TeacherCourseDraftVO.Checks publish(@RequestParam("courseId") Long courseId) {
        return teacherCourseDraftService.publish(courseId);
    }

    @ApiOperation("我的课程（我讲的已上架课 + 我建的草稿课）")
    @GetMapping("my-courses")
    public List<TeacherMyCourseVO> myCourses() {
        return teacherCourseDraftService.listMyCourses();
    }
}
