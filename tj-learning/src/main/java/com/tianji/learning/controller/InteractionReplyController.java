package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-10
 */
@RestController
@RequestMapping("/replies")
@Api(tags = "用户端回答或者评论相关问题")
@RequiredArgsConstructor
public class InteractionReplyController {

    private final IInteractionReplyService replyService;
    @ApiOperation("新增回答或者评论")
    @PostMapping
    public void addReplyOrAnswer(@RequestBody ReplyDTO replyDTO) {
        replyService.addReplyOrAnswer(replyDTO);
    }

//    @ApiOperation("分页查询回答和评论-用户端")
//    @GetMapping("page")
//    public PageDTO<ReplyVO> queryReplyOrAnswerPage(ReplyPageQuery query){
//        return replyService.queryReplyOrAnswerPage(query, Boolean.FALSE);
//    }

}
