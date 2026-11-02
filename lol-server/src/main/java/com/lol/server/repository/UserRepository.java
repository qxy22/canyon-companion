package com.lol.server.repository;

import com.lol.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** 所有还在用的头像 URL，扫孤儿文件时的白名单。 */
    @Query("select u.avatar from User u where u.avatar is not null and u.avatar <> ''")
    List<String> findAllAvatars();

    /**
     * 把一批人批量置为离线（心跳超时兜底）。
     *
     * 用一条 UPDATE 而不是逐个 save：超时可能是几十个人一起超时（比如整层楼断网），
     * 逐个 save 会变成几十条 UPDATE。条件里带 status = 'ONLINE' 是为了幂等 ——
     * 这期间有人自己切成了「游戏中」，不能被他覆盖掉。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.status = 'OFFLINE' where u.id in :ids and u.status = 'ONLINE'")
    int markOffline(@Param("ids") List<Long> ids);
}
