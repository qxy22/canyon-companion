package com.lol.server.repository;

import com.lol.server.entity.ChatRead;
import com.lol.server.entity.ChatReadId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatReadRepository extends JpaRepository<ChatRead, ChatReadId> {

    Optional<ChatRead> findByTeamupIdAndUserId(Long teamupId, Long userId);

    void deleteByTeamupId(Long teamupId);

    void deleteByTeamupIdAndUserId(Long teamupId, Long userId);
}
