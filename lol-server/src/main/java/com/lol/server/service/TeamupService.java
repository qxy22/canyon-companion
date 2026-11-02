package com.lol.server.service;

import com.lol.common.result.PageResult;
import com.lol.common.result.Pages;
import com.lol.server.constant.GameEnums;
import com.lol.server.dto.TeamupRequest;
import com.lol.server.dto.TeamupVO;
import com.lol.server.dto.UserProfileVO;
import com.lol.server.entity.Teamup;
import com.lol.server.redis.CacheKeys;
import com.lol.server.redis.CacheService;
import com.lol.server.repository.TeamupRepository;
import com.lol.server.websocket.RoomNotice;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TeamupService {

    private final TeamupRepository teamupRepository;
    private final UserDirectory directory;
    private final CacheService cache;
    private final ApplicationEventPublisher publisher;

    public TeamupService(TeamupRepository teamupRepository,
                         UserDirectory directory, CacheService cache,
                         ApplicationEventPublisher publisher) {
        this.teamupRepository = teamupRepository;
        this.directory = directory;
        this.cache = cache;
        this.publisher = publisher;
    }

    /** 发布组队，发布人自动成为第一个成员。 */
    public TeamupVO publish(Long ownerId, TeamupRequest req) {
        if (req.getGameMode() == null || !GameEnums.MODES.contains(req.getGameMode())) {
            throw new IllegalArgumentException("模式不合法，可选：RANKED/NORMAL/ARAM");
        }
        // 与 join 同一条规则：一个人同时只能在一支队伍里。
        // 缺了它，已经在队伍里的人还能再发布一支，两边房间都进得去。
        if (!teamupRepository.findByMemberIdOrderByCreatedAtDesc(ownerId).isEmpty()) {
            throw new IllegalArgumentException("你不能同时进入多个队伍，请先在「我的队伍」里退出当前的队伍");
        }
        Teamup teamup = new Teamup();
        teamup.setOwnerId(ownerId);
        teamup.setGameMode(req.getGameMode());
        teamup.setPositions(joinStrings(req.getPositions()));
        teamup.setDescription(req.getDescription());
        int max = req.getMaxSize() == null || req.getMaxSize() < 2 ? 5 : Math.min(req.getMaxSize(), 10);
        teamup.setMaxSize(max);
        teamup.setMemberIds(String.valueOf(ownerId));
        teamup.setStatus("OPEN");
        teamup.setCreatedAt(LocalDateTime.now());
        Teamup saved = teamupRepository.save(teamup);
        invalidateMembers(saved.getId());
        return toVO(saved);
    }

    /**
     * 成员集合变了就把缓存删掉，下次查询会按数据库重建。
     *
     * 这里**只能删、不能改**：成员的变化散落在加入、退出、踢人、解散四个地方，
     * 试图逐个更新集合必然会在某一条路径上漏掉，删掉是最不容易出错的做法。
     */
    private void invalidateMembers(Long teamupId) {
        cache.delete(CacheKeys.teamupMembers(teamupId));
    }

    /**
     * 招募中的组队列表，按发布时间倒序，带分页。
     *
     * 两个刻意的设计：
     * 1. 分页下推到 SQL，只取当前这一页，补齐成员资料的开销就跟着页大小走，
     *    而不是跟着队伍总数走 —— 前端每 2 秒刷新一次，这里必须便宜。
     * 2. 我加入过的队伍直接在 SQL 里排除掉，它们归「我的队伍」那一段显示。
     *    这样每一页都是实打实的 10 条，不会页页不等。
     */
    public PageResult<TeamupVO> list(Long userId, Integer page, Integer size) {
        int p = Pages.clampPage(page);
        int s = Pages.clampSize(size);
        Page<Teamup> slice = teamupRepository.findOpenNotMine(
                "OPEN", userId, PageRequest.of(p - 1, s));
        return PageResult.of(assemble(slice.getContent()), slice.getTotalElements(), p, s);
    }

    /**
     * 我所在的队伍，包含已关闭（满员）的。
     *
     * 招募列表只给 OPEN，可用户最需要对满员的那一支做操作（退出 / 踢人），
     * 少了这个查询，加入满员队伍后就再也找不到出口了。
     */
    public List<TeamupVO> myTeamups(Long userId) {
        return assemble(teamupRepository.findByMemberIdOrderByCreatedAtDesc(userId));
    }

    /** 批量组装列表：先把所有队伍涉及的用户 id 收齐一次查完，避免 N×M 次查询。 */
    private List<TeamupVO> assemble(List<Teamup> teamups) {
        if (teamups.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, List<Long>> membersByTeamup = new LinkedHashMap<>();
        Set<Long> userIds = new HashSet<>();
        for (Teamup t : teamups) {
            List<Long> memberIds = parseIds(t.getMemberIds());
            membersByTeamup.put(t.getId(), memberIds);
            userIds.addAll(memberIds);
            userIds.add(t.getOwnerId());
        }
        Map<Long, UserProfileVO> users = loadProfiles(userIds);

        return teamups.stream()
                .map(t -> toVO(t, membersByTeamup.get(t.getId()), users))
                .collect(Collectors.toList());
    }

    /**
     * 加入组队，满员自动关闭。
     * 加事务 + 悲观写锁（findByIdForUpdate），避免并发加入时互相覆盖成员。
     */
    @Transactional
    public TeamupVO join(Long teamupId, Long userId) {
        Teamup teamup = teamupRepository.findByIdForUpdate(teamupId)
                .orElseThrow(() -> new IllegalArgumentException("组队不存在"));
        if (!"OPEN".equals(teamup.getStatus())) {
            throw new IllegalArgumentException("该组队已满或已结束");
        }
        List<Long> members = parseIds(teamup.getMemberIds());
        if (members.contains(userId)) {
            throw new IllegalArgumentException("你已加入该组队");
        }
        // 一个人同时只能在一支队伍里。
        // 不加这道判断的话会出现：人明明坐在 A 队房间里，又点了 B 队加入，
        // 「我的队伍」变成一串，房间聊天也说不清他到底属于哪个房间。
        List<Teamup> joinable = teamupRepository.findByMemberIdOrderByCreatedAtDesc(userId);
        if (!joinable.isEmpty()) {
            throw new IllegalArgumentException("你不能同时进入多个队伍，请先在「我的队伍」里退出当前的队伍");
        }
        if (members.size() >= teamup.getMaxSize()) {
            // 满员判断放在这里兜底：原来只在加入成功后顺带关闭状态，
            // 一旦状态字段没同步（例如数据被手工改过），就会超员。
            throw new IllegalArgumentException("该组队已满或已结束");
        }
        members.add(userId);
        if (members.size() >= teamup.getMaxSize()) {
            teamup.setStatus("CLOSED");
        }
        teamup.setMemberIds(members.stream().map(String::valueOf).collect(Collectors.joining(",")));
        Teamup saved = teamupRepository.save(teamup);
        invalidateMembers(teamupId);

        Set<Long> ids = new HashSet<>(members);
        ids.add(saved.getOwnerId());
        Map<Long, UserProfileVO> users = loadProfiles(ids);

        // 事件是同步派发的，提示会落库并推给房间里所有人
        publisher.publishEvent(RoomNotice.say(saved.getId(), nameOf(users.get(userId)) + " 加入了房间"));
        return toVO(saved, members, users);
    }

    /**
     * 这个人是不是这支队伍的成员。
     *
     * 房间聊天的收发、上传都用它把关，而且**每条消息都要验一次** ——
     * 队伍成员是会变的（退出 / 被踢），只在握手时验一次，
     * 被踢的人页面开着照样能往房间里发。
     *
     * 「每条一次」在没有缓存的时候等价于「每条消息一次 SELECT」，
     * 房间里五个人正常说话就是每秒几次，这是聊天最贵的一笔。
     * 现在优先问 Redis 的集合成员 Redis 拿不到（没建过 / 挂了）才回落到数据库，
     * 回落后顺便把结果建起来，下一次就命中。
     */
    public boolean isMember(Long teamupId, Long userId) {
        String key = CacheKeys.teamupMembers(teamupId);
        Boolean inSet = cache.isMember(key, String.valueOf(userId));
        if (inSet != null) {
            return inSet;
        }
        // 缓存没有这个成员的时候，必须回数据库取**整组**成员再建索引。
        // 只回填当前这个人是不行的：剩下的成员查回来会被判成「不在队里」——
        // 缓存里的成员会漏人，而漏人比完全没缓存还糟。
        return teamupRepository.findById(teamupId)
                .map(t -> {
                    List<Long> members = parseIds(t.getMemberIds());
                    if (!members.isEmpty()) {
                        cache.addToSet(key, members.stream().map(String::valueOf).collect(Collectors.toList()),
                                CacheKeys.MEMBERS_TTL_SECONDS);
                    }
                    return members.contains(userId);
                })
                .orElse(false);
    }

    /** 不是成员就抛异常。Controller 会把它转成 400 的具体文案。 */
    public void requireMember(Long teamupId, Long userId) {
        if (!isMember(teamupId, userId)) {
            throw new IllegalArgumentException("房间不存在，或你已经不在队伍里");
        }
    }

    /** 我所在的队伍 id。离线清理要用它逐支退，别直接拿实体（那边不持有事务）。 */
    public List<Long> myTeamupIds(Long userId) {
        return teamupRepository.findByMemberIdOrderByCreatedAtDesc(userId).stream()
                .map(Teamup::getId)
                .collect(Collectors.toList());
    }

    /**
     * 退出组队。
     *
     * 最后一个人退出时队伍直接删除 —— 留一条永远招不到人的空队伍没有意义，
     * 而且它会一直挂在数据库里占着 id。
     * 队长退出时把队长交给最早加入的成员，队伍继续存在，不会因为队长走了就散伙。
     *
     * 返回 null 表示这支队伍已经解散。
     */
    @Transactional
    public TeamupVO leave(Long teamupId, Long userId) {
        return leave(teamupId, userId, false);
    }

    /**
     * 退出组队，`offlineExit` 表示是「人离线了，系统替他退的」。
     *
     * 提示文案必须分开写：留在房间里的人需要知道他是掉线走的，
     * 而不是自己点了退出 —— 否则他们会以为队友故意放鸽子。
     * 当事人那边也会收到一条「你已离线，已自动退出队伍」，解释房间为什么关了。
     */
    @Transactional
    public TeamupVO leave(Long teamupId, Long userId, boolean offlineExit) {
        Teamup teamup = teamupRepository.findByIdForUpdate(teamupId)
                .orElseThrow(() -> new IllegalArgumentException("组队不存在"));
        List<Long> members = parseIds(teamup.getMemberIds());
        if (!members.contains(userId)) {
            throw new IllegalArgumentException("你不在这个队伍里");
        }

        boolean wasOwner = teamup.getOwnerId().equals(userId);
        members.remove(userId);
        String who = nameOf(directory.profile(userId));

        if (members.isEmpty()) {
            teamupRepository.delete(teamup);
            invalidateMembers(teamupId);
            // 解散的提示不落库：房间马上就没了，存一条没人看得见的消息没有意义。
            // 先广播给还在线的人，再关连接，最后清消息和文件。
            publisher.publishEvent(RoomNotice.dissolve(teamupId,
                    offlineExit ? who + " 离线了，队伍已解散，房间即将关闭"
                                : who + " 退出了队伍，队伍已解散，房间即将关闭"));
            return null;
        }

        if (wasOwner) {
            teamup.setOwnerId(members.get(0));
        }
        teamup.setMemberIds(joinIds(members));
        // 原本满员关闭的队伍，有人退出后又空出了位置，重新开放招募
        reopenIfHasSlot(teamup, members.size());

        Teamup saved = teamupRepository.save(teamup);
        invalidateMembers(teamupId);
        Set<Long> ids = new HashSet<>(members);
        ids.add(saved.getOwnerId());
        Map<Long, UserProfileVO> users = loadProfiles(ids);

        // 退出者的已读记录要清掉，他的连接也会被断开
        publisher.publishEvent(RoomNotice.evict(teamupId,
                offlineExit ? who + " 离线了，已自动退出房间" : who + " 退出了房间",
                userId,
                offlineExit ? "你已离线，已自动退出队伍" : "你已退出该队伍"));
        return toVO(saved, members, users);
    }

    /** 队长移除某个成员。 */
    @Transactional
    public TeamupVO kick(Long teamupId, Long operatorId, Long targetId) {
        Teamup teamup = teamupRepository.findByIdForUpdate(teamupId)
                .orElseThrow(() -> new IllegalArgumentException("组队不存在"));
        if (!teamup.getOwnerId().equals(operatorId)) {
            throw new IllegalArgumentException("只有队长可以移除成员");
        }
        if (operatorId.equals(targetId)) {
            throw new IllegalArgumentException("队长不能移除自己，要离开请用「退出队伍」");
        }

        List<Long> members = parseIds(teamup.getMemberIds());
        if (!members.contains(targetId)) {
            throw new IllegalArgumentException("这个玩家不在队伍里");
        }

        members.remove(targetId);
        teamup.setMemberIds(joinIds(members));
        reopenIfHasSlot(teamup, members.size());

        Teamup saved = teamupRepository.save(teamup);
        invalidateMembers(teamupId);
        Set<Long> ids = new HashSet<>(members);
        ids.add(saved.getOwnerId());
        Map<Long, UserProfileVO> users = loadProfiles(ids);

        // 被踢的人已经不在 members 里了，得单独查一次，
        // 否则提示里只能写「某位玩家」
        String kicked = nameOf(directory.profile(targetId));

        // evictUserId 带上被踢的人：他的连接会被立刻断开，
        // 否则他页面还开着，看上去仍在房间里。
        // 房间里的人看到「谁被移出了」，当事人自己看到「你被队长移出了房间」。
        // kick 而不是 evict：只有「被别人踢」才额外走全局通知推一条带提示音的提醒。
        // 自己点退出也走 evict 那条路，但那是他自己刚做的动作，不用再吓他一跳。
        publisher.publishEvent(
                RoomNotice.kick(teamupId, kicked + " 被队长移出了房间", targetId, "你被队长移出了房间"));
        return toVO(saved, members, users);
    }

    /** 满员关闭过的队伍，空出位置后重新开放，否则它会永久消失在招募列表里。 */
    private void reopenIfHasSlot(Teamup teamup, int memberCount) {
        if ("CLOSED".equals(teamup.getStatus()) && memberCount < teamup.getMaxSize()) {
            teamup.setStatus("OPEN");
        }
    }

    /** 提示里用的名字。查不到用户时兜个底，别把 null 拼进句子里。 */
    private String nameOf(UserProfileVO user) {
        if (user == null || user.getNickname() == null || user.getNickname().isBlank()) {
            return "某位玩家";
        }
        return user.getNickname();
    }

    private String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * 批量取成员资料，命中缓存就不碰数据库。
     *
     * 招募大厅一次要组装十几支队伍、每支队伍若干个成员，
     * 改之前是每支队伍一次 findAllById；现在整页汇总起来一次 MGET 就够。
     */
    private Map<Long, UserProfileVO> loadProfiles(Set<Long> ids) {
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return new HashMap<>(directory.profiles(ids));
    }

    /** 单条组装（发布后回显用）：自己把成员和用户查出来。 */
    private TeamupVO toVO(Teamup teamup) {
        List<Long> memberIds = parseIds(teamup.getMemberIds());
        Set<Long> ids = new HashSet<>(memberIds);
        ids.add(teamup.getOwnerId());
        return toVO(teamup, memberIds, loadProfiles(ids));
    }

    /** 组装展示对象：memberIds 决定成员顺序（队长在发布时就排在第一位）。 */
    private TeamupVO toVO(Teamup teamup, List<Long> memberIds, Map<Long, UserProfileVO> users) {
        TeamupVO vo = new TeamupVO();
        vo.setId(teamup.getId());
        vo.setOwner(users.get(teamup.getOwnerId()));
        vo.setMembers(memberIds.stream()
                .map(users::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        vo.setGameMode(teamup.getGameMode());
        vo.setPositions(splitStrings(teamup.getPositions()));
        vo.setDescription(teamup.getDescription());
        vo.setMaxSize(teamup.getMaxSize());
        vo.setCurrentSize(memberIds.size());
        vo.setStatus(teamup.getStatus());
        vo.setCreatedAt(teamup.getCreatedAt());
        return vo;
    }

    private String joinStrings(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        return String.join(",", list);
    }

    private List<String> splitStrings(String s) {
        if (s == null || s.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(s.split(",")).map(String::trim)
                .filter(x -> !x.isEmpty()).collect(Collectors.toList());
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
}
