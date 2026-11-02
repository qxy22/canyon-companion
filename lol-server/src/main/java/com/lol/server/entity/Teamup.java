package com.lol.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 开黑组队帖。成员 id 和需要的位置都用逗号分隔的字符串存储，避免多表关联，够毕设使用。
 */
@Entity
@Table(name = "t_teamup")
public class Teamup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 发布人用户 id */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** 模式 code：RANKED / NORMAL / ARAM */
    @Column(name = "game_mode", nullable = false, length = 20)
    private String gameMode;

    /** 需要的位置 code，逗号分隔，如 "TOP,JUNGLE" */
    @Column(length = 100)
    private String positions;

    /** 组队说明 */
    @Column(length = 200)
    private String description;

    /** 队伍人数上限 */
    @Column(name = "max_size", nullable = false)
    private Integer maxSize = 5;

    /** 已加入成员 id，逗号分隔 */
    @Column(name = "member_ids", length = 500)
    private String memberIds;

    /** OPEN 招募中 / CLOSED 已满或已结束 */
    @Column(length = 20)
    private String status = "OPEN";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public String getPositions() {
        return positions;
    }

    public void setPositions(String positions) {
        this.positions = positions;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(Integer maxSize) {
        this.maxSize = maxSize;
    }

    public String getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(String memberIds) {
        this.memberIds = memberIds;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
