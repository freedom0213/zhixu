package com.zhixu.message.service;

import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.message.domain.vo.ChatConversationVO;
import com.zhixu.message.domain.vo.ChatMessageVO;

/**
 * 师生对话（P22）：会话 + 私信 + WebSocket 推送。
 */
public interface IChatService {

    /**
     * 我的会话列表（按最后一条消息时间倒序），带对方名字/头像与我的未读数。
     */
    PageDTO<ChatConversationVO> conversations(Integer pageNo, Integer pageSize);

    /**
     * 与某人的聊天记录。
     * 分页语义与前端页面约定一致：**pageNo=1 是最新的一页**（页内按时间正序），pageNo 越大越早。
     * 副作用：拉取即把我在这条会话上的未读数清零。
     */
    PageDTO<ChatMessageVO> messages(Long otherUserId, Integer pageNo, Integer pageSize);

    /**
     * 发送私信（文本 ≤500 字）；成功后向对方在线的端推送 WebSocket 消息。
     *
     * @return 消息 id
     */
    Long send(Long receiverId, String content);
}
