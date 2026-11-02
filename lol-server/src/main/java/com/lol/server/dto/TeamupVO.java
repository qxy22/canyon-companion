package com.lol.server.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 开黑组队展示对象。
 */
public class TeamupVO {

    private Long id;
    private UserProfileVO owner;
    /** 已加入的成员完整资料，队长排第一。前端据此展示组员并支持点击查看资料。 */
    private List<UserProfileVO> members;
    private String gameMode;
    private List<String> positions;
    private String description;
    private Integer maxSize;
    private Integer currentSize;
    private String status;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserProfileVO getOwner() {
        return owner;
    }

    public void setOwner(UserProfileVO owner) {
        this.owner = owner;
    }

    public List<UserProfileVO> getMembers() {
        return members;
    }

    public void setMembers(List<UserProfileVO> members) {
        this.members = members;
    }

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public List<String> getPositions() {
        return positions;
    }

    public void setPositions(List<String> positions) {
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

    public Integer getCurrentSize() {
        return currentSize;
    }

    public void setCurrentSize(Integer currentSize) {
        this.currentSize = currentSize;
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
