package com.lol.server.repository;

import com.lol.server.entity.Teamup;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamupRepository extends JpaRepository<Teamup, Long> {

    /**
     * 招募中、且我还没加入的队伍，带分页。
     *
     * 两个条件都下推到 SQL，是为了让「每一页 10 条」这个承诺成立：
     * 如果把全部 OPEN 队伍取回来再在内存里剔除我加入的那些，
     * 前端拿到 10 条、过滤掉 2 条，页面上就只剩 8 条，页与页之间忽多忽少。
     *
     * memberIds 是逗号分隔的 id 串，两端补逗号再匹配，首尾元素才不会漏。
     */
    @Query("select t from Teamup t where t.status = :status "
            + "and concat(',', t.memberIds, ',') not like concat('%,', :id, ',%') "
            + "order by t.createdAt desc")
    Page<Teamup> findOpenNotMine(@Param("status") String status, @Param("id") Long id, Pageable pageable);

    /**
     * 我所在的全部队伍，包含已关闭（满员）的。
     *
     * 招募列表只返回 OPEN，而满员的队伍恰恰是最需要操作的那一支 ——
     * 没有这个查询，用户加入满员队伍后就再也找不到「退出队伍」的入口了。
     *
     * memberIds 是逗号分隔的 id 串，两端补逗号再匹配，首尾元素才不会漏。
     */
    @Query("select t from Teamup t where concat(',', t.memberIds, ',') like concat('%,', :id, ',%') order by t.createdAt desc")
    List<Teamup> findByMemberIdOrderByCreatedAtDesc(@Param("id") Long id);

    /**
     * 带悲观写锁的按 id 查询，供「加入组队」使用。
     * 加入是「读出成员字符串 → 追加一个 id → 写回」，没有锁的话两个人同时点加入
     * 会读到同一份旧值，后写的那次覆盖前一次，成员就凭空少一个。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Teamup t where t.id = :id")
    Optional<Teamup> findByIdForUpdate(@Param("id") Long id);
}
