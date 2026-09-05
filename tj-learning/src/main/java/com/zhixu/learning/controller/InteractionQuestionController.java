package com.zhixu.learning.controller;


import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.learning.domain.dto.QuestionFormDTO;
import com.zhixu.learning.domain.query.QuestionPageQuery;
import com.zhixu.learning.domain.vo.QuestionVO;
import com.zhixu.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javassist.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-10
 */
@RestController
@RequestMapping("/questions")
@Api(tags = "用户端互动问题接口")
@RequiredArgsConstructor
public class InteractionQuestionController {

    private final IInteractionQuestionService questionService;

    @ApiOperation("新增问题")
    @PostMapping
    public void saveQuestion(@RequestBody QuestionFormDTO questionDTO) {
         questionService.saveQuestion(questionDTO);
    }

    @ApiOperation("修改问题")
    @PutMapping("{id}")
    //这个id是问题id
    public void updateQuestion(@RequestBody QuestionFormDTO questionDTO,@PathVariable Long id) {
        questionService.updateQuestion(questionDTO,id);
    }

    @ApiOperation("用户端分页查询指定课程的问题")
    @GetMapping("/page")
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        return questionService.queryQuestionPage(query);
    }

    @ApiOperation("根据id查询问题详情")  //id是问题id
    @GetMapping("/{id}")
    public QuestionVO getQuestionById(@PathVariable Long id){
        return questionService.getQuestionById(id);
    }

    @ApiOperation("根据id删除提问")
    @DeleteMapping("/{id}")
    public void deleteQuestionById(@PathVariable Long id) throws NotFoundException {
        questionService.deleteQuestionById(id);
    }





}
