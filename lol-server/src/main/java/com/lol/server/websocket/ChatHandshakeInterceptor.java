package com.lol.server.websocket;

import com.lol.common.util.JwtUtil;
import com.lol.server.service.TeamupService;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 握手阶段就把人验清楚。
 *
 * 两件事必须在这里做：
 * 1. 验 token —— 浏览器 WebSocket 不能自定义请求头，token 只能跟在 URL 上；
 *    而 /ws/** 不在 JwtInterceptor 的 /api/** 范围内，这里不验就等于聊天室大门敞开。
 * 2. 验成员身份 —— 不是这个队的人，连都别让他连上。
 */
@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;
    private final TeamupService teamupService;

    public ChatHandshakeInterceptor(JwtUtil jwtUtil, TeamupService teamupService) {
        this.jwtUtil = jwtUtil;
        this.teamupService = teamupService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (!(request instanceof ServletServerHttpRequest servlet)) {
            reject(response, HttpStatus.BAD_REQUEST);
            return false;
        }

        String token = servlet.getServletRequest().getParameter("token");
        if (token == null || token.isBlank()) {
            reject(response, HttpStatus.UNAUTHORIZED);
            return false;
        }

        Long userId;
        try {
            Claims claims = jwtUtil.parse(token);
            userId = Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            reject(response, HttpStatus.UNAUTHORIZED);
            return false;
        }

        Long teamupId = parseId(servlet.getServletRequest().getParameter("teamupId"));
        if (teamupId == null) {
            reject(response, HttpStatus.BAD_REQUEST);
            return false;
        }

        if (!teamupService.isMember(teamupId, userId)) {
            reject(response, HttpStatus.FORBIDDEN);
            return false;
        }

        // 握手成功后，handler 从会话属性里取这两个值，不用再解析一遍
        attributes.put("userId", userId);
        attributes.put("teamupId", teamupId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 没有握手后要做的事
    }

    private static void reject(ServerHttpResponse response, HttpStatus status) {
        response.setStatusCode(status);
    }

    private static Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
