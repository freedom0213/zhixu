package com.zhixu.learning.controller;

import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.learning.domain.dto.EvaluationFormDTO;
import com.zhixu.learning.domain.query.EvaluationPageQuery;
import com.zhixu.learning.domain.vo.EvaluationVO;
import com.zhixu.learning.service.IEvaluationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/evaluation")
@Api(tags = "课程评价")
@RequiredArgsConstructor
@Validated
public class EvaluationController {
    private final IEvaluationService evaluationService;

    @GetMapping("/page")
    @ApiOperation("分页查询评价")
    public PageDTO<EvaluationVO> page(EvaluationPageQuery query) {
        return evaluationService.queryPage(query);
    }

    @PostMapping
    @ApiOperation("新增评价")
    public void save(@RequestBody @Valid EvaluationFormDTO form) {
        evaluationService.saveEvaluation(form);
    }

    @GetMapping("/{id}")
    public EvaluationVO get(@PathVariable Long id) {
        return evaluationService.queryById(id);
    }

    @PutMapping("/{id}")
    public void update(@PathVariable Long id, @RequestBody @Valid EvaluationFormDTO form) {
        evaluationService.updateEvaluation(id, form);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        evaluationService.deleteEvaluation(id);
    }

    @GetMapping("/evaluated/{courseId}")
    public boolean evaluated(@PathVariable Long courseId) {
        return evaluationService.evaluated(courseId);
    }
}
