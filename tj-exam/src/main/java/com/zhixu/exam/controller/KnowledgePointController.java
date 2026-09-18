package com.zhixu.exam.controller;

import com.zhixu.exam.domain.po.KnowledgePoint;
import com.zhixu.exam.service.IKnowledgePointService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 课程知识点（契约 §4：课程级；录题时只能选，不能现场新建）
 * 网关 /es 前缀 → /es/knowledge-points?courseId=*
 * </p>
 */
@Api(tags = "课程知识点相关接口")
@RequiredArgsConstructor
@RestController
@RequestMapping("/knowledge-points")
public class KnowledgePointController {

    private final IKnowledgePointService knowledgePointService;

    @ApiOperation("按课程查询知识点列表（courseId 缺省返回全部）")
    @GetMapping
    public List<KnowledgePoint> listByCourse(
            @ApiParam("课程id") @RequestParam(value = "courseId", required = false) Long courseId) {
        return knowledgePointService.listByCourse(courseId);
    }
}
