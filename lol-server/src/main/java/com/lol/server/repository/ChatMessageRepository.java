package com.lol.server.repository;

import com.lol.server.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 倒序拉历史，取出来再反转成正序展示。
     * beforeId 为空表示拉最新的那一页。
     */
    @Query("select m from ChatMessage m where m.teamupId = :tid "
            + "and (:before is null or m.id < :before) order by m.id desc")
    List<ChatMessage> findHistory(@Param("tid") Long tid, @Param("before") Long before, Pageable pageable);

    /** 未读数：这个房间里比我已读位置新、且不是我自己发的消息 */
    long countByTeamupIdAndIdGreaterThanAndUserIdNot(Long teamupId, Long lastReadId, Long userId);

    /** 清理房间时先把带文件的消息捞出来，好把文件一起删掉 */
    List<ChatMessage> findByTeamupIdAndTypeIn(Long teamupId, Collection<String> types);

    int deleteByTeamupId(Long teamupId);

    /** 房间里最大的消息 id。进入房间时用「读到最新一条」来消掉未读数 */
    @Query("select coalesce(max(m.id), 0) from ChatMessage m where m.teamupId = :tid")
    Long maxIdOf(@Param("tid") Long tid);

    /** 全部仍在使用的文件 URL，扫孤儿文件时的白名单 */
    @Query("select m.content from ChatMessage m where m.type in ('IMAGE', 'VIDEO', 'STICKER')")
    List<String> findAllFileUrls();
}
