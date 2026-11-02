package com.lol.server.websocket;

import com.lol.server.dto.ChatMessageVO;
import com.lol.server.service.ChatService;
import com.lol.server.service.NotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把成员变动翻译成房间里的提示。
 *
 * 监听而不是被调用，是为了不让 TeamupService 反向依赖聊天这一套（会成环）。
 * 事件是同步派发的，提示会紧接着成员变动发出去。
 */
@Component
public class ChatNoticeListener {

    private static final Logger log = LoggerFactory.getLogger(ChatNoticeListener.class);

    private final ChatService chatService;
    private final ChatWebSocketHandler handler;
    private final NotifyService notify;

    public ChatNoticeListener(ChatService chatService, ChatWebSocketHandler handler,
                              NotifyService notify) {
        this.chatService = chatService;
        this.handler = handler;
        this.notify = notify;
    }

    @EventListener
    public void on(RoomNotice notice) {
        if (notice == null || notice.teamupId() == null) {
            return;
        }
        try {
            if (notice.dissolve()) {
                handleDissolve(notice);
            } else {
                handleMemberChange(notice);
            }
        } catch (Exception e) {
            // 提示发不出去不能影响成员变动本身 —— 那才是主流程
            log.error("房间提示处理失败，房间 {}", notice.teamupId(), e);
        }
    }

    /** 有人加入 / 退出 / 被踢：先落一条系统消息，再广播，最后把该踢下线的人断开。 */
    private void handleMemberChange(RoomNotice notice) {
        ChatMessageVO vo = chatService.saveSystem(notice.teamupId(), notice.text());
        handler.broadcast(notice.teamupId(), vo);

        if (notice.evictUserId() != null) {
            if (notice.kicked()) {
                // 被踢的人可能压根不在房间页面上（人在大厅、甚至关了标签页），
                // 只靠房间那条连接是通知不到的 —— 这里再走全局通道推一次。
                // 放在断开连接之前发，顺序上更稳：房间连接断了之后前端才知道自己出去了。
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("teamupId", notice.teamupId());
                payload.put("reason", notice.evictReason() != null ? notice.evictReason() : "你被移出了房间");
                payload.put("text", notice.text());
                notify.push(notice.evictUserId(), "kicked", payload);
            }

            // 他已经不是成员了，已读记录留着只会让未读数算错
            chatService.removeReadOf(notice.teamupId(), notice.evictUserId());
            handler.closeSessionsOf(
                    notice.teamupId(),
                    notice.evictUserId(),
                    notice.evictReason() != null ? notice.evictReason() : "你已不在该队伍里");
        }
    }

    /** 解散：先让房间里的人看见提示，再关连接，最后清消息和文件。顺序不能反。 */
    private void handleDissolve(RoomNotice notice) {
        handler.broadcastNotice(notice.teamupId(), notice.text());
        handler.closeRoom(notice.teamupId(), "队伍已解散");
        chatService.deleteRoom(notice.teamupId());
    }
}
