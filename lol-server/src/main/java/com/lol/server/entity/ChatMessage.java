package com.lol.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 房间聊天消息。
 *
 * 房间就是一支组队（teamup），不额外建「房间」表：队伍解散聊天就该跟着没，
 * 再维护一张房间表只会多一处要同步的状态。
 *
 * 这里刻意不建到 teamup 的外键 —— leave() 最后一人退出时是直接 delete 队伍行的，
 * 有外键就删不动了。成员关系由应用层的 assertMember 保证。
 */
@Entity
@Table(name = "t_chat_message", indexes = {
        @Index(name = "idx_room_id", columnList = "teamup_id, id")
})
public class ChatMessage {

    /** TEXT 文本 / IMAGE 图片 / VIDEO 视频 / STICKER 表情包 / SYSTEM 进出提示 */
    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_IMAGE = "IMAGE";
    public static final String TYPE_VIDEO = "VIDEO";
    public static final String TYPE_STICKER = "STICKER";
    public static final String TYPE_SYSTEM = "SYSTEM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属房间（组队 id） */
    @Column(name = "teamup_id", nullable = false)
    private Long teamupId;

    /** 发送者。系统消息为 null */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "type", nullable = false, length = 16)
    private String type;

    /** 文本内容，或图片/视频的访问 URL */
    @Column(name = "content", length = 1000)
    private String content;

    /** 原始文件名，文件消息才有，用于展示「xxx.mp4」 */
    @Column(name = "file_name", length = 255)
    private String fileName;

    /** 文件字节数，用于展示和删除前核对 */
    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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
