package com.zhixu.message.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 握手拦截：用**一次性票据**确认身份（P22）。
 * -----------------------------------------------------------------------------
 * 网关对 /sms/ws 原样转发（SCG 请求改写与 WS 代理冲突，见网关侧注释），
 * 所以这里拿不到网关注入的 user-info —— 身份改由「先 REST 换票据、再凭票据握手」保证：
 * 票据由已鉴权的 REST 接口签发、一次性、30 秒过期。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class UserIdHandshakeInterceptor implements HandshakeInterceptor {

    private final ChatTicketStore ticketStore;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 票据在查询参数里（/ws?ticket=xxx；网关已把 /sms/ws 剥成 /ws）
        String query = request.getURI().getQuery();
        String ticket = null;
        if (query != null) {
            for (String kv : query.split("&")) {
                if (kv.startsWith("ticket=")) {
                    ticket = kv.substring("ticket=".length());
                    break;
                }
            }
        }
        Long userId = ticketStore.consume(ticket);
        if (userId == null) {
            log.info("聊天 WS 握手拒绝：票据缺失/过期/已使用");
            return false;
        }
        attributes.put("userId", userId);
        log.info("聊天 WS 握手通过 userId={}", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
