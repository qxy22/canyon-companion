package com.lol.server.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lol.server.dto.ChatMessageVO;
import com.lol.server.entity.ChatMessage;
import com.lol.server.service.ChatService;
import com.lol.server.service.FileStorageService;
import com.lol.server.service.TeamupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 房间聊天的长连接。
 *
 * 两件必须在运行时反复确认的事：
 * 1. 每条消息都重新校验成员身份 —— 只在握手时验一次不够，
 *    被踢出队伍的人页面还开着，照样能往房间里发消息。
 * 2. 文件地址必须是本地签发的前缀 —— 否则随便给个外链就能当消息发出去。
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    /** 允许客户端发上来的消息类型。系统消息只能由服务端产生。 */
    private static final Set<String> ALLOWED_CLIENT_TYPES =
            Set.of(ChatMessage.TYPE_TEXT, ChatMessage.TYPE_IMAGE,
                    ChatMessage.TYPE_VIDEO, ChatMessage.TYPE_STICKER);

    /** 限流：5 秒内最多 10 条。刷屏会直接把房间聊天的体验毁掉 */
    private static final long WINDOW_MS = 5_000L;
    private static final int MAX_PER_WINDOW = 10;

    /** 房间 → 这个房间里的全部连接。同一个人开多个标签页会有多个连接，这是正常的 */
    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    /** 限流计数：userId → [窗口起点毫秒, 条数] */
    private final Map<Long, long[]> rate = new ConcurrentHashMap<>();

    private final ChatService chatService;
    private final TeamupService teamupService;
    private final ObjectMapper mapper;

    public ChatWebSocketHandler(ChatService chatService, TeamupService teamupService, ObjectMapper mapper) {
        this.chatService = chatService;
        this.teamupService = teamupService;
        this.mapper = mapper;
    }

    /** 给前端的统一信封。event 决定怎么解析 data。 */
    public record Out(String event, Object data, String message) {
    }

    // ------------------------------------------------------------ 生命周期

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long teamupId = attr(session, "teamupId");
        if (teamupId == null) {
            closeQuietly(session);
            return;
        }
        rooms.computeIfAbsent(teamupId, k -> new CopyOnWriteArraySet<>()).add(session);
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

    private void drop(WebSocketSession session) {
        Long teamupId = attr(session, "teamupId");
        if (teamupId == null) {
            return;
        }
        CopyOnWriteArraySet<WebSocketSession> set = rooms.get(teamupId);
        if (set == null) {
            return;
        }
        set.remove(session);
        if (set.isEmpty()) {
            rooms.remove(teamupId, set);
        }
    }

    // ------------------------------------------------------------ 收消息

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long teamupId = attr(session, "teamupId");
        Long userId = attr(session, "userId");
        if (teamupId == null || userId == null) {
            closeQuietly(session);
            return;
        }

        JsonNode node;
        try {
            node = mapper.readTree(message.getPayload());
        } catch (Exception e) {
            sendError(session, "消息格式不对，请刷新页面重试");
            return;
        }

        String type = node.path("type").asText("").trim().toUpperCase();
        if ("PING".equals(type)) {
            send(session, new Out("pong", null, null));
            return;
        }
        if (!ALLOWED_CLIENT_TYPES.contains(type)) {
            sendError(session, "不支持的消息类型");
            return;
        }
        if (!passRateLimit(userId)) {
            sendError(session, "发得太快了，缓一下再发");
            return;
        }

        // 每次都重新确认：他可能刚刚被队长踢了，或者自己退出了
        try {
            teamupService.requireMember(teamupId, userId);
        } catch (IllegalArgumentException e) {
            send(session, new Out("closed", null, "你已不在该队伍里"));
            closeQuietly(session);
            return;
        }

        String content;
        String fileName = null;
        Long fileSize = null;
        try {
            if (ChatMessage.TYPE_TEXT.equals(type)) {
                content = chatService.normalizeText(node.path("content").asText(""));
            } else {
                content = node.path("content").asText("");
                if (!content.startsWith(FileStorageService.URL_PREFIX)) {
                    sendError(session, "文件地址无效，请重新上传");
                    return;
                }
                fileName = blankToNull(node.path("fileName").asText(""));
                fileSize = node.hasNonNull("fileSize") ? node.get("fileSize").asLong() : null;
            }
        } catch (IllegalArgumentException e) {
            sendError(session, e.getMessage());
            return;
        }

        ChatMessageVO vo = chatService.save(teamupId, userId, type, content, fileName, fileSize);
        broadcast(teamupId, vo);
    }

    // ------------------------------------------------------------ 对外：广播与断开

    /** 广播一条已入库的消息，房间里所有人（含发送者自己）都收到同一份。 */
    public void broadcast(Long teamupId, ChatMessageVO vo) {
        sendAll(teamupId, new Out("message", vo, null));
    }

    /** 广播一条不入库的提示（比如解散前那句「队伍已解散」）。 */
    public void broadcastNotice(Long teamupId, String text) {
        sendAll(teamupId, new Out("notice", null, text));
    }

    /** 把某个人从房间里踢下线。踢人和他自己退出都会走到这里。 */
    public void closeSessionsOf(Long teamupId, Long userId, String reason) {
        CopyOnWriteArraySet<WebSocketSession> set = rooms.get(teamupId);
        if (set == null) {
            return;
        }
        for (WebSocketSession s : new ArrayList<>(set)) {
            if (userId.equals(attr(s, "userId"))) {
                send(s, new Out("closed", null, reason));
                closeQuietly(s, new CloseStatus(4001, reason));
                set.remove(s);
            }
        }
    }

    /** 关掉整个房间的所有连接。队伍解散时用。 */
    public void closeRoom(Long teamupId, String reason) {
        CopyOnWriteArraySet<WebSocketSession> set = rooms.remove(teamupId);
        if (set == null) {
            return;
        }
        for (WebSocketSession s : set) {
            send(s, new Out("closed", null, reason));
            closeQuietly(s, new CloseStatus(4002, reason));
        }
    }

    // ------------------------------------------------------------ 内部

    private void sendAll(Long teamupId, Out payload) {
        CopyOnWriteArraySet<WebSocketSession> set = rooms.get(teamupId);
        if (set == null || set.isEmpty()) {
            return;
        }
        try {
            TextMessage msg = new TextMessage(mapper.writeValueAsString(payload));
            for (WebSocketSession s : set) {
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
        } catch (Exception e) {
            log.error("广播消息失败，房间 {}", teamupId, e);
        }
    }

    private void send(WebSocketSession session, Out payload) {
        try {
            send(session, new TextMessage(mapper.writeValueAsString(payload)));
        } catch (Exception e) {
            log.warn("下发消息失败：{}", e.getMessage());
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

    private void sendError(WebSocketSession session, String text) {
        send(session, new Out("error", null, text));
    }

    private void closeQuietly(WebSocketSession session) {
        closeQuietly(session, CloseStatus.NORMAL);
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException ignored) {
            // 关不掉就算了，对面已经断了
        }
    }

    private boolean passRateLimit(Long userId) {
        long now = System.currentTimeMillis();
        long[] slot = rate.computeIfAbsent(userId, k -> new long[]{now, 0});
        synchronized (slot) {
            if (now - slot[0] > WINDOW_MS) {
                slot[0] = now;
                slot[1] = 0;
            }
            slot[1]++;
            return slot[1] <= MAX_PER_WINDOW;
        }
    }

    @SuppressWarnings("unchecked")
    private Long attr(WebSocketSession session, String key) {
        Object v = session.getAttributes().get(key);
        return v instanceof Long l ? l : null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** 当前在线连接数，排查问题用 */
    public int onlineCount(Long teamupId) {
        CopyOnWriteArraySet<WebSocketSession> set = rooms.get(teamupId);
        return set == null ? 0 : set.size();
    }

    /** 房间里所有连接的成员 id，去重。排查"为什么他没收到"时用得上 */
    public List<Long> memberIdsOnline(Long teamupId) {
        CopyOnWriteArraySet<WebSocketSession> set = rooms.get(teamupId);
        if (set == null) {
            return List.of();
        }
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        for (WebSocketSession s : set) {
            Long uid = attr(s, "userId");
            if (uid != null) {
                ids.add(uid);
            }
        }
        return List.copyOf(ids);
    }
}
