package com.lol.server.config;

import com.lol.server.websocket.ChatHandshakeInterceptor;
import com.lol.server.websocket.ChatWebSocketHandler;
import com.lol.server.websocket.NotifyHandshakeInterceptor;
import com.lol.server.websocket.NotifyWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ChatWebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler handler;
    private final ChatHandshakeInterceptor interceptor;
    private final NotifyWebSocketHandler notifyHandler;
    private final NotifyHandshakeInterceptor notifyInterceptor;

    public ChatWebSocketConfig(ChatWebSocketHandler handler, ChatHandshakeInterceptor interceptor,
                               NotifyWebSocketHandler notifyHandler,
                               NotifyHandshakeInterceptor notifyInterceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
        this.notifyHandler = notifyHandler;
        this.notifyInterceptor = notifyInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 前端连的是 ws://<后端>/ws/chat?token=xxx&teamupId=xxx
        // 允许任意来源：开发时前端在 5173、后端在 8080，是跨域的
        registry.addHandler(handler, "/ws/chat")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns("*");

        // 全局通知：只带 token，不绑队伍。好友邀请、被踢提醒走这一条。
        registry.addHandler(notifyHandler, "/ws/notify")
                .addInterceptors(notifyInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
