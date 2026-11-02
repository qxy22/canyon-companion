package com.lol.server.service;

import com.lol.server.dto.UserProfileVO;
import com.lol.server.entity.Friend;
import com.lol.server.entity.Teamup;
import com.lol.server.repository.FriendRepository;
import com.lol.server.repository.TeamupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 好友关系与组队邀请。
 *
 * 好友不需要对方同意：点「加好友」立刻成为好友，同时给对方推一条提醒 + 提示音。
 * 这是有意的取舍 —— 毕设场景里多一层"申请 / 同意"只会让人看不懂，
 * 而即时互加能让"加好友 → 邀请组队"这条路径一步到位。
 */
@Service
public class FriendService {

    private final FriendRepository friendRepository;
    private final TeamupRepository teamupRepository;
    private final UserDirectory directory;
    private final NotifyService notify;

    public FriendService(FriendRepository friendRepository, TeamupRepository teamupRepository,
                         UserDirectory directory, NotifyService notify) {
        this.friendRepository = friendRepository;
        this.teamupRepository = teamupRepository;
        this.directory = directory;
        this.notify = notify;
    }

    /** 我的好友列表，最近加的排前面。 */
    public List<UserProfileVO> list(Long userId) {
        List<Long> ids = friendRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(Friend::getFriendId).collect(Collectors.toList());
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        // 一次批量取资料（走 Redis），不要一个人一次查询
        Map<Long, UserProfileVO> users = directory.profiles(ids);
        return ids.stream().map(users::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public boolean isFriend(Long userId, Long otherId) {
        if (userId == null || otherId == null || userId.equals(otherId)) {
            return false;
        }
        return friendRepository.existsByUserIdAndFriendId(userId, otherId);
    }

    /** 好友数。顶栏只显示数量，没必要把每个人的资料都取出来。 */
    public long count(Long userId) {
        return friendRepository.countByUserId(userId);
    }

    /**
     * 加好友。双向各写一行，并通知对方。
     */
    @Transactional
    public UserProfileVO add(Long userId, Long targetId) {
        if (targetId == null) {
            throw new IllegalArgumentException("参数不完整");
        }
        if (userId.equals(targetId)) {
            throw new IllegalArgumentException("不能把自己加为好友");
        }
        UserProfileVO target = directory.profile(targetId);
        if (target == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (friendRepository.existsByUserIdAndFriendId(userId, targetId)) {
            throw new IllegalArgumentException("你们已经是好友了");
        }

        LocalDateTime now = LocalDateTime.now();
        friendRepository.save(new Friend(userId, targetId, now));
        friendRepository.save(new Friend(targetId, userId, now));

        // 被加的那一方才知道"多了个好友"，提醒他一下
        UserProfileVO me = directory.profile(userId);
        if (me != null) {
            notify.push(targetId, "friend_added", me);
        }
        return target;
    }

    /**
     * 删好友。两行一起删，否则对方列表里还留着你，点邀请会一直被拒。
     */
    @Transactional
    public void remove(Long userId, Long targetId) {
        if (targetId == null) {
            throw new IllegalArgumentException("参数不完整");
        }
        friendRepository.deleteByUserIdAndFriendId(userId, targetId);
        friendRepository.deleteByUserIdAndFriendId(targetId, userId);
    }

    /**
     * 邀请好友加入我当前的队伍。
     *
     * 这里把能提前拦下的问题都拦掉：没队伍、队伍满了、对方在别的队伍里。
     * 让对方收到一条点开才发现进不去的邀请，比不给邀请更糟。
     */
    public void invite(Long userId, Long friendId) {
        if (friendId == null) {
            throw new IllegalArgumentException("参数不完整");
        }
        if (!isFriend(userId, friendId)) {
            throw new IllegalArgumentException("只能邀请好友");
        }

        Teamup team = myTeamOf(userId);
        if (team == null) {
            throw new IllegalArgumentException("你还没有加入队伍，先发布或加入一个队伍再邀请好友");
        }

        List<Long> members = parseIds(team.getMemberIds());
        if (members.contains(friendId)) {
            throw new IllegalArgumentException("对方已经在你的队伍里了");
        }
        if (members.size() >= team.getMaxSize()) {
            throw new IllegalArgumentException("你的队伍已经满了，空出位置后才能邀请");
        }

        List<Teamup> theirs = teamupRepository.findByMemberIdOrderByCreatedAtDesc(friendId);
        if (!theirs.isEmpty()) {
            UserProfileVO friend = directory.profile(friendId);
            throw new IllegalArgumentException(nameOf(friend) + " 已经在别的队伍里了，得先退出那边");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("teamupId", team.getId());
        payload.put("inviter", directory.profile(userId));
        payload.put("gameMode", team.getGameMode());
        payload.put("description", team.getDescription());
        payload.put("positions", splitStrings(team.getPositions()));
        payload.put("currentSize", members.size());
        payload.put("maxSize", team.getMaxSize());

        notify.push(friendId, "friend_invite", payload);
    }

    /** 我所在的队伍。单人单队之后理论上只有一个，取第一个即可。 */
    private Teamup myTeamOf(Long userId) {
        List<Teamup> mine = teamupRepository.findByMemberIdOrderByCreatedAtDesc(userId);
        return mine.isEmpty() ? null : mine.get(0);
    }

    private String nameOf(UserProfileVO user) {
        if (user == null || user.getNickname() == null || user.getNickname().isBlank()) {
            return "对方";
        }
        return user.getNickname();
    }

    private List<Long> parseIds(String s) {
        List<Long> ids = new ArrayList<>();
        if (s == null || s.isBlank()) {
            return ids;
        }
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                ids.add(Long.valueOf(t));
            }
        }
        return ids;
    }

    private List<String> splitStrings(String s) {
        if (s == null || s.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(s.split(",")).map(String::trim)
                .filter(x -> !x.isEmpty()).collect(Collectors.toList());
    }
}
