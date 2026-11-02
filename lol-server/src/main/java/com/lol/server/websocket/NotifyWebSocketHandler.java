package com.lol.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 全局通知通道（/ws/notify）。
 *
 * 和房间聊天（/ws/chat）是两条独立的连接，刻意不合并：
 * - 聊天连接绑着某一支队伍，退队/解散就该断开；
 * - 好友邀请、被踢提醒这些事跟"当前在哪个房间"没关系，人在大厅里也得收得到。
 *
 * 这条连接只做服务端往下推，客户端除了心跳不发别的东西。
 */
@Component
public class NotifyWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotifyWebSocketHandler.class);

    /** userId → 这个人的全部连接。同一账号开多个标签页会有多个连接 */
    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> sessions =
            new ConcurrentHashMap<>();

    private final ObjectMapper mapper;

    public NotifyWebSocketHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 给前端的事件信封，和房间聊天保持同一套字段名。 */
    public record Out(String event, Object data, String message) {
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = attr(session, "userId");
        if (userId == null) {
            closeQuietly(session);
            return;
        }
        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        drop(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        drop(session);
        closeQuietly(session);
    }

    /** 心跳。客户端每 30 秒发一次 PING，避免中间层把空闲连接掐掉。 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (message.getPayload() != null && message.getPayload().contains("PING")) {
            send(session, new Out("pong", null, null));
        }
    }

    private void drop(WebSocketSession session) {
        Long userId = attr(session, "userId");
        if (userId == null) {
            return;
        }
        CopyOnWriteArraySet<WebSocketSession> set = sessions.get(userId);
        if (set == null) {
            return;
        }
        set.remove(session);
        if (set.isEmpty()) {
            sessions.remove(userId, set);
        }
    }

    /**
     * 推给某个人的所有在线连接。
     *
     * 对方不在线不算失败 —— 这类提醒本来就是即时的，没有离线补发。
     * 所以找不到连接直接返回，不记错误。
     */
    public void push(Long userId, String event, Object data) {
        CopyOnWriteArraySet<WebSocketSession> set = sessions.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        TextMessage msg;
        try {
            msg = new TextMessage(mapper.writeValueAsString(new Out(event, data, null)));
        } catch (Exception e) {
            log.error("通知序列化失败，事件 {}", event, e);
            return;
        }
        for (WebSocketSession s : new ArrayList<>(set)) {
            if (!s.isOpen()) {
                set.remove(s);
                continue;
            }
            try {
                send(s, msg);
            } catch (IOException e) {
                set.remove(s);
                closeQuietly(s);
            }
        }
    }

    /** 在线连接数，排查"为什么他没收到"时用 */
    public int onlineUsers() {
        return sessions.size();
    }

    private void send(WebSocketSession session, Out payload) {
        try {
            send(session, new TextMessage(mapper.writeValueAsString(payload)));
        } catch (Exception e) {
            log.warn("下发通知失败：{}", e.getMessage());
        }
    }

    /** 同一个会话上的并发发送会抛异常，逐个会话串行化 */
    private void send(WebSocketSession session, TextMessage msg) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(msg);
            }
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        } catch (IOException ignored) {
            // 对面已经断了
        }
    }

    private Long attr(WebSocketSession session, String key) {
        Object v = session.getAttributes().get(key);
        return v instanceof Long l ? l : null;
    }
}
