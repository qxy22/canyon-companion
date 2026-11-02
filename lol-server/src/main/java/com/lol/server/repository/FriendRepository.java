package com.lol.server.repository;

import com.lol.server.entity.Friend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    /** 我的好友，最近加的排前面。 */
    List<Friend> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 判断是不是好友时只看单向就够了 —— 关系由 FriendService 保证成对写入。 */
    boolean existsByUserIdAndFriendId(Long userId, Long friendId);

    /** 顶栏那个「好友」按钮上的数字。只数行，不取资料。 */
    long countByUserId(Long userId);

    /** 删除也必须成对调用，否则会留下"对方还认为你俩是好友"的单边关系。 */
    void deleteByUserIdAndFriendId(Long userId, Long friendId);
}
