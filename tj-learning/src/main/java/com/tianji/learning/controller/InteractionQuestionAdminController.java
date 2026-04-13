package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author 虎哥
 */
@RestController
@RequestMapping("/admin")
@Api(tags = "管理端互动问答相关接口")
@RequiredArgsConstructor
public class InteractionQuestionAdminController {

    private final IInteractionQuestionService questionService;

    @ApiOperation("管理端分页查询互动问答")
    @GetMapping("/questions/page")
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        return questionService.queryQuestionPageAdmin(query);
    }

    @PutMapping("/questions/{id}/hidden/{hidden}")
    @ApiOperation("管理端隐藏或者显示问题")
    public void hiddenQuestion(@PathVariable("id") Integer id, @PathVariable("hidden") Boolean hidden) {
        questionService.hiddenQuestionAdmin(id,hidden);
    }

    @GetMapping("/questions/{id}")
    @ApiOperation("管理端根据id查询问题详情")
    public QuestionAdminVO queryQuestionByIdAdmin(@ApiParam(value = "问题id", example = "1") @PathVariable("id") Integer id) {
        return questionService.queryQuestionByIdAdmin(id);
    }



}
