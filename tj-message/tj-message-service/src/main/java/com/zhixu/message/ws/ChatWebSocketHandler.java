package com.zhixu.message.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 师生对话 · WebSocket（P22）。
 * -----------------------------------------------------------------------------
 * 身份来自握手请求头 `user-info`（UserIdHandshakeInterceptor 写进 attributes）——
 * 该头只能由网关从 token 解出后注入，服务端不自己解析 token。
 *
 * 在线表：userId → 该用户当前打开的**所有**端（同一个人开两个标签页都能收到推送）。
 * 推送失败不影响消息落库：对方下次打开会话列表仍能看到（离线消息的兜底是 REST）。
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Map<Long, Set<WebSocketSession>> ONLINE = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long uid = userIdOf(session);
        if (uid == null) {
            // 没有身份的握手在拦截器里就已被拒，这里只是兜底
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE);
            } catch (Exception e) {
                log.debug("关闭无身份连接失败", e);
            }
            return;
        }
        ONLINE.computeIfAbsent(uid, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.debug("聊天 WS 上线 userId={}，当前连接数={}", uid, ONLINE.get(uid).size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long uid = userIdOf(session);
        if (uid == null) {
            return;
        }
        Set<WebSocketSession> set = ONLINE.get(uid);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                ONLINE.remove(uid);
            }
        }
        log.debug("聊天 WS 下线 userId={} status={}", uid, status);
    }

    /** 客户端不发消息（发送走 REST 落库），收到文本只当心跳忽略 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // no-op
    }

    /** 把 payload 推给某用户的所有在线端 */
    public static void pushTo(Long userId, String payload) {
        Set<WebSocketSession> set = ONLINE.get(userId);
        if (set == null || set.isEmpty()) {
            return; // 对方不在线：不是错误，离线消息靠 REST 兜底
        }
        for (WebSocketSession s : set) {
            try {
                if (s.isOpen()) {
                    // 同一会话可能被多线程并发推送，sendMessage 非线程安全 → 串行化
                    synchronized (s) {
                        s.sendMessage(new TextMessage(payload));
                    }
                }
            } catch (Exception e) {
                log.warn("聊天 WS 推送失败 userId={}", userId, e);
            }
        }
    }

    private Long userIdOf(WebSocketSession session) {
        Object v = session.getAttributes().get("userId");
        if (v instanceof Long) {
            return (Long) v;
        }
        try {
            return v == null ? null : Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
