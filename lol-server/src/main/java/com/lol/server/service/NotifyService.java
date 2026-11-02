package com.lol.server.service;

import com.lol.server.websocket.NotifyWebSocketHandler;
import org.springframework.stereotype.Service;

/**
 * 全局通知的唯一出口。
 *
 * 业务代码只说「通知谁、发生什么」，不关心连接怎么找、发失败怎么办。
 * 通知发不出去不能影响主流程 —— 加好友、踢人本身已经成功了，
 * 对方只是没听到那声响而已，所以这里不抛异常。
 */
@Service
public class NotifyService {

    private final NotifyWebSocketHandler handler;

    public NotifyService(NotifyWebSocketHandler handler) {
        this.handler = handler;
    }

    public void push(Long userId, String event, Object data) {
        if (userId == null || event == null) {
            return;
        }
        handler.push(userId, event, data);
    }

    /** 当前有几个人挂着通知连接，排查用 */
    public int onlineUsers() {
        return handler.onlineUsers();
    }
}
