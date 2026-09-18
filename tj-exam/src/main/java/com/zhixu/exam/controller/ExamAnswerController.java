package com.zhixu.exam.controller;

import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.exam.domain.dto.ExamSubmitDTO;
import com.zhixu.exam.domain.vo.ExamRecordPageVO;
import com.zhixu.exam.domain.vo.ExamResultVO;
import com.zhixu.exam.domain.vo.ExamStartVO;
import com.zhixu.exam.domain.vo.ExamStatisticsVO;
import com.zhixu.exam.domain.vo.PendingReviewSummaryVO;
import com.zhixu.exam.domain.vo.StudentCourseExamVO;
import com.zhixu.exam.service.IExamAnswerService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 作答与判分（p14）
 * -----------------------------------------------------------------------------
 * 为什么单独一个 controller：`ExamController` 是**讲师端**的卷子管理（建卷 / 发布 / 出卷），
 * 这一组是**作答侧**（学生答、判分、回看）与**批改侧**（讲师看作答、统计）。
 * 两者语义不同、权限不同，混在一起以后必然互相牵制。
 *
 * 路径刻意**不与会出卷的 `POST /exams`（保存试卷草稿）**重叠：
 * 旧前端把 `POST /exams` 当"拉题"用，是历史遗留的语义冲突，这里另开 `/exams/{id}/start`。
 */
@Api(tags = "作答与判分相关接口")
@RequiredArgsConstructor
@RestController
public class ExamAnswerController {

    private final IExamAnswerService examAnswerService;

    // ------------------------------------------------------------------ 学生侧

    @ApiOperation("某门课程下已发布的试卷（学生视角，带我的作答状态）")
    @GetMapping("/exams/published")
    public List<StudentCourseExamVO> publishedOfCourse(@ApiParam("课程id") @RequestParam("courseId") Long courseId) {
        return examAnswerService.publishedOfCourse(courseId);
    }

    @ApiOperation("按小节开始作答（随堂练习：学生从课程学习页的「考试」小节进入）")
    @PostMapping("/exams/section/{sectionId}/start")
    public ExamStartVO startBySection(@ApiParam("小节id") @PathVariable("sectionId") Long sectionId) {
        return examAnswerService.startBySection(sectionId);
    }

    @ApiOperation("开始作答（返回题目，不含答案；已交卷会如实拒绝）")
    @PostMapping("/exams/{id}/start")
    public ExamStartVO start(@ApiParam("试卷id") @PathVariable("id") Long examId) {
        return examAnswerService.start(examId);
    }

    @ApiOperation("交卷（自动判分；重复交卷幂等返回既有成绩）")
    @PostMapping("/exams/{id}/submit")
    public ExamResultVO submit(@ApiParam("试卷id") @PathVariable("id") Long examId,
                              @RequestBody(required = false) ExamSubmitDTO dto) {
        return examAnswerService.submit(examId, dto);
    }

    @ApiOperation("我的这场成绩（答卷回看，读作答时冻结的快照）")
    @GetMapping("/exams/{id}/result")
    public ExamResultVO myResult(@ApiParam("试卷id") @PathVariable("id") Long examId) {
        return examAnswerService.myResult(examId);
    }

    @ApiOperation("我的作答记录（分页）")
    @GetMapping("/exam-records/page")
    public PageDTO<ExamRecordPageVO> myRecords(
            @RequestParam(value = "pageNo", required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        return examAnswerService.myRecords(pageNo, pageSize);
    }

    // ------------------------------------------------------------------ 讲师侧

    @ApiOperation("待批改总览（讲师视角：我建的卷里各场的已交卷 / 待复核份数）")
    @GetMapping("/exam-records/pending-review")
    public PendingReviewSummaryVO pendingReview() {
        return examAnswerService.pendingReview();
    }

    @ApiOperation("某场考试的作答列表（批改用）")
    @GetMapping("/exams/{id}/records")
    public List<ExamRecordPageVO> recordsOfExam(@ApiParam("试卷id") @PathVariable("id") Long examId) {
        return examAnswerService.recordsOfExam(examId);
    }

    @ApiOperation("某场考试的成绩统计（只计已交卷）")
    @GetMapping("/exams/{id}/statistics")
    public ExamStatisticsVO statistics(@ApiParam("试卷id") @PathVariable("id") Long examId) {
        return examAnswerService.statistics(examId);
    }

    @ApiOperation("看某条作答记录的答卷（讲师批改用）")
    @GetMapping("/exam-records/{id}/result")
    public ExamResultVO resultOfRecord(@ApiParam("作答记录id") @PathVariable("id") Long recordId) {
        return examAnswerService.resultOfRecord(recordId);
    }

    @ApiOperation("复核成绩（讲师确认；选择题已自动判分）")
    @PutMapping("/exam-records/{id}/review")
    public Map<String, Object> review(@ApiParam("作答记录id") @PathVariable("id") Long recordId) {
        examAnswerService.review(recordId);
        return java.util.Collections.singletonMap("ok", true);
    }
}
