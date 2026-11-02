package com.lol.server.repository;

import com.lol.server.entity.MatchRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, Long> {

    boolean existsByUserIdAndGameId(Long userId, Long gameId);

    Page<MatchRecord> findByUserIdOrderByPlayedAtDesc(Long userId, Pageable pageable);

    long countByUserId(Long userId);
}
