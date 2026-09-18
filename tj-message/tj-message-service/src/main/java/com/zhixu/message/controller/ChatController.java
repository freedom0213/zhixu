package com.zhixu.message.controller;

import com.zhixu.common.domain.R;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.message.domain.vo.ChatConversationVO;
import com.zhixu.message.domain.vo.ChatMessageVO;
import com.zhixu.common.utils.UserContext;
import com.zhixu.message.service.IChatService;
import com.zhixu.message.ws.ChatTicketStore;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 师生对话（P22）。
 * -----------------------------------------------------------------------------
 * 响应统一 **R 包装**（{"code":200,"data":...}）—— 前端页面判 `res.code == 200`，
 * 与本服务其它接口的裸返回风格不同，别照抄。
 *
 * WebSocket：外部路径 `/sms/ws?token=<jwt>`（握手经网关，浏览器带不了自定义请求头），
 * 服务内路径是 `/ws`，见 ws/ChatWebSocketHandler。
 */
@Api("师生对话")
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final IChatService chatService;
    private final ChatTicketStore ticketStore;

    @ApiOperation("我的会话列表（带未读数）")
    @GetMapping("/conversations")
    public R<PageDTO<ChatConversationVO>> conversations(
            @ApiParam("页码") @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @ApiParam("每页条数") @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return R.ok(chatService.conversations(pageNo, pageSize));
    }

    @ApiOperation("与某人的聊天记录（pageNo=1 最新一页，页内正序；拉取即清我方未读）")
    @GetMapping("/messages")
    public R<PageDTO<ChatMessageVO>> messages(
            @ApiParam("对方用户id") @RequestParam("otherUserId") Long otherUserId,
            @ApiParam("页码") @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @ApiParam("每页条数") @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return R.ok(chatService.messages(otherUserId, pageNo, pageSize));
    }

    @ApiOperation("换取 WS 握手票据（一次性，30 秒有效）")
    @GetMapping("/ws-ticket")
    public R<String> wsTicket() {
        return R.ok(ticketStore.issue(UserContext.getUser()));
    }

    @ApiOperation("发送私信（文本 ≤500 字；成功后实时推给对方在线的端）")
    @PostMapping("/messages")
    public R<Long> send(@RequestBody Map<String, Object> body) {
        Long receiverId = body.get("userId") == null ? null : Long.valueOf(String.valueOf(body.get("userId")));
        Object content = body.get("content");
        return R.ok(chatService.send(receiverId, content == null ? null : String.valueOf(content)));
    }
}
