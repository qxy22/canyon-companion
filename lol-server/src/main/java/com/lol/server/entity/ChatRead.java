package com.lol.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 成员在某个房间里读到了哪条消息。
 * 未读数 = 该房间里 id 大于 last_read_id 且不是自己发的消息条数。
 */
@Entity
@Table(name = "t_chat_read")
@IdClass(ChatReadId.class)
public class ChatRead {

    @Id
    @Column(name = "teamup_id", nullable = false)
    private Long teamupId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 已读到的最大消息 id。没有就是一条都没读过 */
    @Column(name = "last_read_id", nullable = false)
    private Long lastReadId = 0L;

    public Long getTeamupId() {
        return teamupId;
    }

    public void setTeamupId(Long teamupId) {
        this.teamupId = teamupId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getLastReadId() {
        return lastReadId;
    }

    public void setLastReadId(Long lastReadId) {
        this.lastReadId = lastReadId;
    }
}
