package com.lol.server.entity;

import java.io.Serializable;
import java.util.Objects;

/** t_chat_read 的复合主键：一个成员在一个房间里只有一行已读位置。 */
public class ChatReadId implements Serializable {

    private Long teamupId;
    private Long userId;

    public ChatReadId() {
    }

    public ChatReadId(Long teamupId, Long userId) {
        this.teamupId = teamupId;
        this.userId = userId;
    }

    public Long getTeamupId() {
        return teamupId;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatReadId other)) return false;
        return Objects.equals(teamupId, other.teamupId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamupId, userId);
    }
}
