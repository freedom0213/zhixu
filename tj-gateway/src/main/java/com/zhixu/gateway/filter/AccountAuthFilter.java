package com.zhixu.gateway.filter;

import com.zhixu.authsdk.gateway.util.AuthUtil;
import com.zhixu.common.domain.R;
import com.zhixu.common.domain.dto.LoginUserDTO;
import com.zhixu.gateway.config.AuthProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static com.zhixu.auth.common.constants.JwtConstants.AUTHORIZATION_HEADER;
import static com.zhixu.auth.common.constants.JwtConstants.USER_HEADER;

@Component
public class AccountAuthFilter implements GlobalFilter, Ordered {

    private final AuthUtil authUtil;
    private final AuthProperties authProperties;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    public AccountAuthFilter(AuthUtil authUtil, AuthProperties authProperties) {
        this.authUtil = authUtil;
        this.authProperties = authProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1.获取请求request信息
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethodValue();
        String path = request.getPath().toString();
        String antPath = method + ":" + path;

        // 2.🔴 先把客户端自己带的 user-info 剥掉（P18 安全修复）
        //    「我是谁」只能由网关注入：以前免登录路径是**原样转发**的，客户端随手伪造一个
        //    `user-info: 2` 就能以别人的身份访问（本地实测：不带任何 token 也能拿到讲师的数据）。
        //    已登录请求虽然在下面会覆盖它，但身份这种事不该指望"后面会被覆盖"——先删干净。
        //
        //    ⚠️ P22：/sms/ws 的 WS 升级请求走这条改写会以 1002 Protocol error 断开
        //    （SCG 请求改写与 WS 代理的冲突）→ 这条路径**原样转发**，
        //    身份改用「一次性票据」解决：客户端先调 /sms/ws-ticket（正常鉴权）
        //    换 30s 一次性票据，再凭票据握手（见 message-service 的 ChatTicketStore）。
        if (path.startsWith("/sms/ws")) {
            return chain.filter(exchange);
        }
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
                .headers(headers -> headers.remove(USER_HEADER));

        // 3.判断是否是无需登录的路径（注意：即使免登录，user-info 也已经被剥掉了）
        if(isExcludePath(antPath)){
            // 直接放行
            return chain.filter(exchange.mutate().request(builder.build()).build());
        }

        // 4.尝试获取用户信息
        List<String> authHeaders = exchange.getRequest().getHeaders().get(AUTHORIZATION_HEADER);
        String token = (authHeaders == null || authHeaders.isEmpty()) ? "" : authHeaders.get(0);
        // 4.1 WebSocket 握手的例外（P22）：浏览器 new WebSocket() **带不了自定义请求头**，
        //     所以 /sms/ws 允许用 `?token=<jwt>` 查询参数认证 —— 仅此一条路径，其它路径仍只认请求头
        //     （token 进 URL 会被网关访问日志记录，所以绝不扩大范围）。
        if (token.isEmpty() && path.startsWith("/sms/ws")) {
            String qp = request.getQueryParams().getFirst("token");
            if (qp != null && !qp.isEmpty()) {
                token = qp;
            }
        }
        R<LoginUserDTO> r = authUtil.parseToken(token);

        // 5.只有 token 校验通过的请求，才由网关写入身份
        if(r.success()){
            builder.header(USER_HEADER, r.getData().getUserId().toString());
        }

        // 6.校验权限
        authUtil.checkAuth(antPath, r);

        // 7.放行
        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    private boolean isExcludePath(String antPath) {
        for (String pathPattern : authProperties.getExcludePath()) {
            if(antPathMatcher.match(pathPattern, antPath)){
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return 1000;
    }
}
