package com.zhixu.exam.controller;

import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.exam.domain.dto.ExamFormDTO;
import com.zhixu.exam.domain.query.ExamPageQuery;
import com.zhixu.exam.domain.vo.ExamDetailVO;
import com.zhixu.exam.domain.vo.ExamPageVO;
import com.zhixu.exam.service.IExamService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * <p>
 * 考试（契约 §9 的 7 个接口；网关 /es 前缀 → /es/exams）
 * 主线：新建试卷引用题库 → 发布时生成快照 → 学生答卷 / 批改 / 统计读同一份快照
 * </p>
 */
@Api(tags = "考试管理相关接口")
@RequiredArgsConstructor
@RestController
@RequestMapping("/exams")
public class ExamController {

    private final IExamService examService;

    @ApiOperation("考试分页")
    @GetMapping("page")
    public PageDTO<ExamPageVO> queryExamPage(ExamPageQuery query) {
        return examService.queryExamPage(query);
    }

    @ApiOperation("状态计数（全部 / 草稿 / 已发布 / 已结束）")
    @GetMapping("count")
    public Map<String, Integer> countExams(@ApiParam("只看我创建的：1/true") @RequestParam(value = "mine", required = false) Boolean mine) {
        return examService.countExams(mine);
    }

    @ApiOperation("考试详情（已发布读快照；含展开后的题目）")
    @GetMapping("{id}")
    public ExamDetailVO queryExamDetail(@ApiParam("考试id") @PathVariable("id") Long id) {
        return examService.queryExamDetail(id);
    }

    @ApiOperation("新建草稿（草稿只存引用，不生成快照）")
    @PostMapping
    public Long saveExamDraft(@Valid @RequestBody ExamFormDTO dto) {
        return examService.saveExamDraft(dto);
    }

    @ApiOperation("更新草稿（仅本人草稿）")
    @PutMapping("{id}")
    public void updateExamDraft(
            @ApiParam("考试id") @PathVariable("id") Long id,
            @Valid @RequestBody ExamFormDTO dto) {
        examService.updateExamDraft(id, dto);
    }

    @ApiOperation("发布 —— 此步生成试卷快照（paperVersion / snapshotItems）")
    @PostMapping("{id}/publish")
    public Map<String, Object> publishExam(
            @ApiParam("考试id") @PathVariable("id") Long id,
            @RequestBody(required = false) ExamFormDTO settings) {
        return examService.publishExam(id, settings);
    }

    @ApiOperation("删除（仅本人草稿）")
    @DeleteMapping("{id}")
    public void deleteExam(@ApiParam("考试id") @PathVariable("id") Long id) {
        examService.deleteExam(id);
    }
}
