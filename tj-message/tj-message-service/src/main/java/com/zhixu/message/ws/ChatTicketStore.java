package com.zhixu.message.ws;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WS 握手票据（P22）。
 * -----------------------------------------------------------------------------
 * 浏览器 new WebSocket() 带不了自定义请求头，而网关对 /sms/ws 又是原样转发
 * （SCG 的请求改写与 WS 代理冲突），所以握手身份用**一次性票据**：
 *   ① 客户端先带 token 调 REST `GET /sms/ws-ticket`（正常鉴权）；
 *   ② 服务签发 30 秒一次性 ticket，绑定用户 id；
 *   ③ 客户端 `new WebSocket("/sms/ws?ticket=…")`，握手时消费掉（一次性 + 过期作废）。
 * 拿不回 101 就是票据无效，不会把别人的连接错认成自己。
 */
@Component
public class ChatTicketStore {

    private static final long TTL_MS = 30_000;

    /** Java 11 没有 record，用静态内部类 */
    private static final class Ticket {
        private final Long userId;
        private final long expiresAt;

        private Ticket(Long userId, long expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public String issue(Long userId) {
        cleanup();
        String ticket = UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, new Ticket(userId, System.currentTimeMillis() + TTL_MS));
        return ticket;
    }

    /** 消费票据（一次性）：有效返回用户 id，否则 null */
    public Long consume(String ticket) {
        if (ticket == null || ticket.isEmpty()) {
            return null;
        }
        Ticket t = tickets.remove(ticket);
        if (t == null || t.expiresAt < System.currentTimeMillis()) {
            return null;
        }
        return t.userId;
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(e -> e.getValue().expiresAt < now);
    }
}
