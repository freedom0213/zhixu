package com.zhixu.learning.service;

import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.learning.domain.dto.ReplyDTO;
import com.zhixu.learning.domain.po.InteractionReply;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixu.learning.domain.query.ReplyPageQuery;
import com.zhixu.learning.domain.vo.ReplyVO;

/**
 * <p>
 * 互动问题的回答或评论 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-10
 */
public interface IInteractionReplyService extends IService<InteractionReply> {

    void addReplyOrAnswer(ReplyDTO replyDTO);

    //PageDTO<ReplyVO> queryReplyOrAnswerPage(ReplyPageQuery query, Boolean aFalse);
}
