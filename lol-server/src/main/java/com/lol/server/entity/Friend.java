package com.lol.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 好友关系。
 *
 * 存两行（A→B 和 B→A）而不是一行加方向标志，是为了让「查我的好友」永远只有
 * 一个条件：user_id = 我。少掉一次 OR / 两次查询，也不用在内存里把双向关系摊平。
 * 代价是增删都要动两行，所以只能成对操作，见 FriendService。
 */
@Entity
@Table(name = "t_friend", uniqueConstraints = {
        @UniqueConstraint(name = "uk_friend_user_target", columnNames = {"user_id", "friend_id"})
})
public class Friend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "friend_id", nullable = false)
    private Long friendId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Friend() {
    }

    public Friend(Long userId, Long friendId, LocalDateTime createdAt) {
        this.userId = userId;
        this.friendId = friendId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getFriendId() {
        return friendId;
    }

    public void setFriendId(Long friendId) {
        this.friendId = friendId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
