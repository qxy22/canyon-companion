package com.lol.server.websocket;

/**
 * 房间里的成员变动通知。
 *
 * 用事件而不是让 TeamupService 直接调聊天服务，是因为：
 * TeamupService → ChatService → TeamupService 会成环（聊天要查成员、成员变动要发提示）。
 * 事件把这两件事解耦，TeamupService 只管说「谁怎么了」，具体怎么提示由监听方决定。
 *
 * @param teamupId    房间（队伍）id
 * @param text        房间里要显示的那句话，第三人称，比如「小飞 被队长移出了房间」
 * @param evictUserId 要被断开连接的人（被踢 / 自己退出），没有就传 null
 * @param dissolve    队伍是不是解散了。解散时只广播，不落库 —— 房间马上就没了
 * @param evictReason 对当事人本人说的那句话。跟 text 分开是因为：留在房间里的人
 *                    需要知道「谁走了」，而当事人只需要知道「我为什么出去了」。
 *                    拿 text 凑合会让他看到一句「小飞 被队长移出了房间」的第三人称叙述。
 * @param kicked      是不是「被别人踢了」。自己点退出也是 evict，但那不需要再弹一个
 *                    「你被移出房间」的通知 —— 他刚刚亲手点的，再提醒一次很怪。
 */
public record RoomNotice(Long teamupId, String text, Long evictUserId, boolean dissolve,
                         String evictReason, boolean kicked) {

    /** 只是往房间里说一句话，不涉及踢人。 */
    public static RoomNotice say(Long teamupId, String text) {
        return new RoomNotice(teamupId, text, null, false, null, false);
    }

    /** 广播一条提示，同时把某人踢下线。用于「自己退出」。 */
    public static RoomNotice evict(Long teamupId, String text, Long evictUserId, String evictReason) {
        return new RoomNotice(teamupId, text, evictUserId, false, evictReason, false);
    }

    /** 同上，但当事人是被队长踢掉的 —— 会额外推一条带提示音的全局通知。 */
    public static RoomNotice kick(Long teamupId, String text, Long evictUserId, String evictReason) {
        return new RoomNotice(teamupId, text, evictUserId, false, evictReason, true);
    }

    /** 队伍解散：广播完就关房间、清消息。 */
    public static RoomNotice dissolve(Long teamupId, String text) {
        return new RoomNotice(teamupId, text, null, true, null, false);
    }
}
