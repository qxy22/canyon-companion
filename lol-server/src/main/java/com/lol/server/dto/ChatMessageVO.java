package com.lol.server.dto;

import java.time.LocalDateTime;

/**
 * 一条聊天消息。系统消息的 user 为 null，前端据此渲染成居中的灰字提示。
 */
public class ChatMessageVO {

    private Long id;
    private Long teamupId;
    private UserProfileVO user;
    private String type;
    private String content;
    private String fileName;
    private Long fileSize;
    private LocalDateTime createdAt;

    // 这里刻意不带「是不是我发的」这个标记。
    // WebSocket 广播是同一份 JSON 发给房间里所有人的，把 mine 算进去，
    // 别人收到的消息也会显示成他发的。这是「看的人是谁」决定的，属于前端的事：
    // msg.user.id === 我的 id。

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTeamupId() {
        return teamupId;
    }

    public void setTeamupId(Long teamupId) {
        this.teamupId = teamupId;
    }

    public UserProfileVO getUser() {
        return user;
    }

    public void setUser(UserProfileVO user) {
        this.user = user;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
