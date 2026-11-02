package com.lol.server.service;

import com.lol.common.result.PageResult;
import com.lol.common.result.Pages;
import com.lol.server.dto.RecommendVO;
import com.lol.server.dto.UserProfileVO;
import com.lol.server.entity.User;
import com.lol.server.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MatchService {

    private static final List<String> RANK_ORDER = List.of(
            "IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM",
            "EMERALD", "DIAMOND", "MASTER", "GRANDMASTER", "CHALLENGER");

    private final UserRepository userRepository;
    private final UserDirectory directory;
    private final PresenceService presence;

    public MatchService(UserRepository userRepository, UserDirectory directory, PresenceService presence) {
        this.userRepository = userRepository;
        this.directory = directory;
        this.presence = presence;
    }

    /** 多维搜索：位置、段位、性别、昵称/召唤师名关键字，分页。 */
    public PageResult<UserProfileVO> search(String position, String rankTier, String gender,
                                            String keyword, int page, int size) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (position != null && !position.isBlank()) {
                predicates.add(cb.equal(root.get("position"), position));
            }
            if (rankTier != null && !rankTier.isBlank()) {
                predicates.add(cb.equal(root.get("rankTier"), rankTier));
            }
            if (gender != null && !gender.isBlank()) {
                predicates.add(cb.equal(root.get("gender"), gender));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("nickname"), like),
                        cb.like(root.get("summonerName"), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);
        Page<User> result = userRepository.findAll(spec, pageable);
        List<UserProfileVO> list = result.getContent().stream()
                .map(UserProfileVO::from)
                .collect(Collectors.toList());
        return PageResult.of(list, result.getTotalElements(), safePage, safeSize);
    }

    /**
     * 在线可开黑用户列表，最近活跃的排在前。
     *
     * 原来这里是 findAll() 把整张用户表捞出来在内存里筛 —— 每刷一次大厅就扫一遍全表。
     * 现在从 Redis 的在线索引里取 id，再批量取资料，代价跟在线人数成正比，
     * 跟注册用户总数再无关系。
     *
     * 索引不可用（Redis 没起）时退回原来的查法，慢但结果一致。
     *
     * 分页无法下推：在线 id 是在 Redis 的 ZSET 里排好的，排序本身就是索引给的，
     * 只能在应用层切出第 page 页。好在这条路径不碰数据库。
     */
    public PageResult<UserProfileVO> onlineList(Integer page, Integer size) {
        int p = Pages.clampPage(page);
        int s = Pages.clampSize(size);

        List<Long> ids = presence.onlineIds();
        List<UserProfileVO> all;
        if (ids == null) {
            all = onlineListFromDb();
        } else {
            all = byActivityOrder(ids, directory.profiles(ids));
        }
        return PageResult.of(Pages.slice(all, p, s), (long) all.size(), p, s);
    }

    /**
     * 匹配推荐：段位相近 + 位置互补，按匹配分倒序，带分页。
     * 同样改走在线索引 —— 只在线的人有可能被推荐，没必要把离线的一起扫进来。
     *
     * 这里也只能在应用层切页：匹配分是现算的，数据库里没有这一列可排。
     */
    public PageResult<RecommendVO> recommend(Long userId, Integer page, Integer size) {
        int p = Pages.clampPage(page);
        int s = Pages.clampSize(size);

        List<RecommendVO> all = scoreAll(userId);
        return PageResult.of(Pages.slice(all, p, s), (long) all.size(), p, s);
    }

    /** 给所有在线候选人算出匹配分并排好序，不截断 —— 截多少由分页决定。 */
    private List<RecommendVO> scoreAll(Long userId) {
        UserProfileVO me = directory.profile(userId);
        if (me == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        List<Long> ids = presence.onlineIds();
        Map<Long, UserProfileVO> profiles;
        if (ids == null) {
            // 索引不可用时退回：从库里挑出在线的人
            List<Long> online = userRepository.findAll().stream()
                    .filter(u -> "ONLINE".equals(u.getStatus()))
                    .map(User::getId)
                    .collect(Collectors.toList());
            profiles = directory.profiles(online);
            ids = online.stream().filter(profiles::containsKey).collect(Collectors.toList());
        } else {
            profiles = directory.profiles(ids);
        }

        List<RecommendVO> result = new ArrayList<>();
        for (Long id : ids) {
            if (id.equals(userId)) {
                continue;
            }
            UserProfileVO candidate = profiles.get(id);
            if (candidate == null) {
                continue;
            }
            int score = 40;
            List<String> reasons = new ArrayList<>();
            int diff = Math.abs(rankIndex(candidate.getRankTier()) - rankIndex(me.getRankTier()));
            if (diff == 0) {
                score += 30;
                reasons.add("段位相同");
            } else if (diff <= 2) {
                score += 15;
                reasons.add("段位相近");
            }
            if (me.getPosition() != null && candidate.getPosition() != null
                    && !me.getPosition().equals(candidate.getPosition())) {
                score += 30;
                reasons.add("位置互补");
            }
            result.add(new RecommendVO(candidate, Math.min(score, 100), reasons));
        }
        // 同分时必须再按 id 排一次，不能只按匹配分。
        // 入参 ids 是 Redis 在线索引的顺序（按最近活跃时间），活跃时间每时每刻都在变，
        // 于是同分的两个人这次查是 A 在前、下一次可能是 B 在前 —— 用户翻到第 2 页，
        // 会看到第 1 页已经看过的人。加上 id 这个不会变的次级键，同分的顺序就固定了。
        result.sort(Comparator.comparing(RecommendVO::getMatchScore).reversed()
                .thenComparing(r -> r.getUser().getId()));
        return result;
    }

    /** Redis 不可用时的兜底路径，行为和加缓存之前完全一致。 */
    private List<UserProfileVO> onlineListFromDb() {
        List<Long> ids = userRepository.findAll().stream()
                .filter(u -> "ONLINE".equals(u.getStatus()))
                .sorted((a, b) -> {
                    if (a.getLastActiveAt() == null) {
                        return 1;
                    }
                    if (b.getLastActiveAt() == null) {
                        return -1;
                    }
                    return b.getLastActiveAt().compareTo(a.getLastActiveAt());
                })
                .map(User::getId)
                .collect(Collectors.toList());
        Map<Long, UserProfileVO> profiles = directory.profiles(ids);
        return byActivityOrder(ids, profiles);
    }

    /** 按「索引里的顺序 / 库里排出的顺序」输出，资料取不到的跳过。 */
    private List<UserProfileVO> byActivityOrder(List<Long> ids, Map<Long, UserProfileVO> profiles) {
        List<UserProfileVO> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            UserProfileVO vo = profiles.get(id);
            if (vo != null) {
                out.add(vo);
            }
        }
        return out;
    }

    private int rankIndex(String rankTier) {
        if (rankTier == null) {
            return -1;
        }
        int idx = RANK_ORDER.indexOf(rankTier);
        return idx < 0 ? -1 : idx;
    }
}
