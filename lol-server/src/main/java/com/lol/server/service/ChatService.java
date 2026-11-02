package com.lol.server.service;

import com.lol.server.dto.ChatMessageVO;
import com.lol.server.dto.UserProfileVO;
import com.lol.server.entity.ChatMessage;
import com.lol.server.entity.ChatRead;
import com.lol.server.entity.Teamup;
import com.lol.server.repository.ChatMessageRepository;
import com.lol.server.repository.ChatReadRepository;
import com.lol.server.repository.TeamupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 房间聊天。
 *
 * 房间就是一支组队，没有独立的「房间」实体：队伍解散，聊天就该跟着没，
 * 再维护一张房间表只会多一处需要同步的状态。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** 单条文本最长 1000 字，跟数据库列宽一致 */
    public static final int MAX_TEXT = 1000;

    private static final List<String> FILE_TYPES = List.of(
            ChatMessage.TYPE_IMAGE, ChatMessage.TYPE_VIDEO, ChatMessage.TYPE_STICKER);

    private final ChatMessageRepository messageRepository;
    private final ChatReadRepository readRepository;
    private final TeamupRepository teamupRepository;
    private final UserDirectory directory;
    private final TeamupService teamupService;
    private final FileStorageService storage;

    public ChatService(ChatMessageRepository messageRepository, ChatReadRepository readRepository,
                       TeamupRepository teamupRepository, UserDirectory directory,
                       TeamupService teamupService, FileStorageService storage) {
        this.messageRepository = messageRepository;
        this.readRepository = readRepository;
        this.teamupRepository = teamupRepository;
        this.directory = directory;
        this.teamupService = teamupService;
        this.storage = storage;
    }

    // ------------------------------------------------------------------ 读

    /**
     * 拉历史消息，正序返回。beforeId 为空表示拉最新的一页。
     */
    public List<ChatMessageVO> history(Long teamupId, Long userId, Long beforeId, Integer size) {
        teamupService.requireMember(teamupId, userId);
        int n = Math.min(Math.max(size == null ? 30 : size, 1), 50);

        List<ChatMessage> rows = new ArrayList<>(
                messageRepository.findHistory(teamupId, beforeId, PageRequest.of(0, n)));
        Collections.reverse(rows);
        return toVOs(rows);
    }

    /** 各房间的未读数。返回 teamupId → 未读条数，没有未读的房间不出现。 */
    public Map<Long, Long> unreadSummary(Long userId) {
        List<Teamup> mine = teamupRepository.findByMemberIdOrderByCreatedAtDesc(userId);
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Teamup t : mine) {
            long last = readRepository.findByTeamupIdAndUserId(t.getId(), userId)
                    .map(ChatRead::getLastReadId).orElse(0L);
            long count = messageRepository.countByTeamupIdAndIdGreaterThanAndUserIdNot(t.getId(), last, userId);
            if (count > 0) {
                result.put(t.getId(), count);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ 写

    /**
     * 存一条消息。
     * 成员身份由调用方先用 teamupService.requireMember 校验过 —— 每来一条都要验，
     * 否则页面开着被踢出队伍的人还能继续往房间里发消息。
     */
    @Transactional
    public ChatMessageVO save(Long teamupId, Long userId, String type, String content,
                              String fileName, Long fileSize) {
        ChatMessage m = new ChatMessage();
        m.setTeamupId(teamupId);
        m.setUserId(userId);
        m.setType(type);
        m.setContent(content);
        m.setFileName(fileName);
        m.setFileSize(fileSize);
        m.setCreatedAt(LocalDateTime.now());
        return toVO(messageRepository.save(m), directory.profile(userId));
    }

    /** 系统提示（谁进来了、谁被移出去了）。没有发送者。 */
    @Transactional
    public ChatMessageVO saveSystem(Long teamupId, String text) {
        ChatMessage m = new ChatMessage();
        m.setTeamupId(teamupId);
        m.setUserId(null);
        m.setType(ChatMessage.TYPE_SYSTEM);
        m.setContent(text);
        m.setCreatedAt(LocalDateTime.now());
        return toVO(messageRepository.save(m), null);
    }

    /** 校验文本消息，返回可以安全入库的内容。 */
    public String normalizeText(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        String text = raw.trim();
        if (text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("单条消息最多 " + MAX_TEXT + " 字");
        }
        return text;
    }

    // ------------------------------------------------------------------ 已读

    /** 把已读位置推到 lastId（不给就推到当前最新一条）。 */
    @Transactional
    public void markRead(Long teamupId, Long userId, Long lastId) {
        if (!teamupService.isMember(teamupId, userId)) {
            return;
        }
        long target = lastId != null && lastId > 0
                ? lastId
                : Objects.requireNonNullElse(messageRepository.maxIdOf(teamupId), 0L);

        ChatRead row = readRepository.findByTeamupIdAndUserId(teamupId, userId).orElseGet(ChatRead::new);
        row.setTeamupId(teamupId);
        row.setUserId(userId);
        // 只往前推，避免前端传了个更小的值把未读数又变出来
        if (target > row.getLastReadId()) {
            row.setLastReadId(target);
            readRepository.save(row);
        }
    }

    // ------------------------------------------------------------------ 清理

    /**
     * 队伍解散：消息、文件、已读记录一起清掉。
     * 顺序不能反 —— 先按消息里的 URL 删文件，再删消息行，否则 URL 就没了，文件变成孤儿。
     */
    @Transactional
    public void deleteRoom(Long teamupId) {
        List<ChatMessage> fileMessages = messageRepository.findByTeamupIdAndTypeIn(teamupId, FILE_TYPES);
        for (ChatMessage m : fileMessages) {
            storage.delete(m.getContent());
        }
        messageRepository.deleteByTeamupId(teamupId);
        readRepository.deleteByTeamupId(teamupId);
        log.info("房间 {} 已清理：{} 条消息，{} 个文件", teamupId, fileMessages.size(), fileMessages.size());
    }

    /** 有人退出或被踢：他的已读记录没必要留着，消息保留给剩下的人看。 */
    @Transactional
    public void removeReadOf(Long teamupId, Long userId) {
        readRepository.deleteByTeamupIdAndUserId(teamupId, userId);
    }

    /** 聊天里还在用的文件 URL，扫孤儿文件时是白名单的一部分（另一部分是用户头像）。 */
    public Set<String> liveFileUrls() {
        return new HashSet<>(messageRepository.findAllFileUrls());
    }

    // ------------------------------------------------------------------ 组装

    /**
     * 一批消息的发送者资料。
     *
     * 聊天是全项目最热的路径 —— 房间里每个人说一句话都要组装一次 VO，
     * 走 UserDirectory 后同一个人的资料只会在缓存 miss 时查一次库。
     */
    private List<ChatMessageVO> toVOs(List<ChatMessage> rows) {
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> ids = rows.stream().map(ChatMessage::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, UserProfileVO> users = directory.profiles(ids);
        return rows.stream().map(m -> toVO(m, users.get(m.getUserId()))).collect(Collectors.toList());
    }

    private ChatMessageVO toVO(ChatMessage m, UserProfileVO user) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(m.getId());
        vo.setTeamupId(m.getTeamupId());
        vo.setUser(user);
        vo.setType(m.getType());
        vo.setContent(m.getContent());
        vo.setFileName(m.getFileName());
        vo.setFileSize(m.getFileSize());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }
}
