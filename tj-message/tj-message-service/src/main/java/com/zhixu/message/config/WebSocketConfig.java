package com.zhixu.message.config;

import com.zhixu.message.ws.ChatWebSocketHandler;
import com.zhixu.message.ws.UserIdHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 师生对话 · WebSocket 注册（P22）。
 * 服务内路径 /ws；经网关访问是 /sms/ws（网关 StripPrefix 后到这儿）。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final UserIdHandshakeInterceptor userIdHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws")
                .addInterceptors(userIdHandshakeInterceptor)
                // 前端从 localhost:10010 / 部署域名连过来，跨域握手放行（身份仍由握手头把关）
                .setAllowedOriginPatterns("*");
    }
}
