package com.lol.server.service;

import com.lol.server.entity.User;
import com.lol.server.redis.CacheKeys;
import com.lol.server.redis.CacheService;
import com.lol.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 人离线了就把他从队伍里摘出去。
 *
 * 为什么这件事要单独一个类：离线有三条路径（关网页、手动切离线、心跳超时），
 * 它们分散在 ProfileService 和 PresenceService 里。如果各自直接调 TeamupService，
 * 退队的规则就会散成三份，早晚有一处漏掉或者写得不一样。
 * 这里收敛成一个入口，谁发现「他离线了」就叫一声。
 *
 * 为什么要等 6 秒（{@link #GRACE_MS}）：
 * 刷新页面时 pagehide 也会触发，前端会照常报一次离线。如果立刻退队，
 * 用户按个 F5 就被踢出房间 —— 这是绝对不能出现的。
 * 所以先记一笔，几秒后再看：这期间他重新上线就把这笔取消；
 * 真没回来（关页面、关浏览器）才动手。
 * 心跳超时那条路不需要等 —— 90 秒没动静的人是真的走了。
 *
 * 为什么要 REQUIRES_NEW：
 * 心跳超时是批量处理的，一次可能几十上百人。某一支队伍退不掉（被锁、数据不一致）
 * 不能把整批「置为离线」一起回滚，那会让更多人变成幽灵在线。
 * 每支队伍一个独立事务，坏一个不影响其余。
 */
@Service
public class OfflineTeamupCleaner {

    private static final Logger log = LoggerFactory.getLogger(OfflineTeamupCleaner.class);

    /**
     * 宽限时间。刷新页面通常 1 秒内就重新上线了，留 6 秒足够容纳慢一点的情况；
     * 也不能太长 —— 队长掉了线，队友要能很快知道他不会回来了。
     */
    static final long GRACE_MS = 6_000L;

    /** 到期时间表：userId → 该动手的时间戳。 */
    private final Map<Long, Long> pending = new ConcurrentHashMap<>();

    private final TeamupService teamups;
    private final UserRepository users;
    private final CacheService cache;
    private final UserDirectory directory;
    private final NotifyService notify;
    private final TransactionTemplate tx;

    public OfflineTeamupCleaner(TeamupService teamups, UserRepository users, CacheService cache,
                                UserDirectory directory, NotifyService notify,
                                PlatformTransactionManager txManager) {
        this.teamups = teamups;
        this.users = users;
        this.cache = cache;
        this.directory = directory;
        this.notify = notify;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 报离线时调用：先排队，等过了宽限期再确认。 */
    public void schedule(Long userId) {
        if (userId == null) {
            return;
        }
        pending.put(userId, System.currentTimeMillis() + GRACE_MS);
    }

    /** 又上线了（含刷新页面后重新进站）：撤掉排队中的那一笔。 */
    public void cancel(Long userId) {
        if (userId != null) {
            pending.remove(userId);
        }
    }

    /** 心跳超时：人已经确实走了，不用再等，当场退。 */
    public void exitNow(Long userId) {
        exit(userId, true);
    }

    /**
     * 每秒看一眼有没有到期的。
     *
     * 用 remove(key, value) 而不是先 get 再 remove：这期间用户可能又掉线了一次
     * （到期时间被改新），那时不该按旧时间动手，交给下一轮。
     */
    @Scheduled(fixedDelay = 1_000L, initialDelay = 1_000L)
    public void flush() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, Long> entry : pending.entrySet()) {
            if (now < entry.getValue()) {
                continue;
            }
            if (pending.remove(entry.getKey(), entry.getValue())) {
                exit(entry.getKey(), false);
            }
        }
    }

    /**
     * @param verified 调用方是否已经确认过此人离线（心跳超时那条路）。为 false 时再验一次。
     */
    private void exit(Long userId, boolean verified) {
        try {
            List<Long> teamupIds = teamups.myTeamupIds(userId);
            if (teamupIds.isEmpty()) {
                return;
            }
            if (!verified && !stillOffline(userId)) {
                // 多半是刷新页面：pagehide 报了离线，新页面又立刻报回在线
                log.debug("用户 {} 已回到在线状态，取消自动退队", userId);
                return;
            }
            for (Long teamupId : teamupIds) {
                try {
                    tx.executeWithoutResult(status -> teamups.leave(teamupId, userId, true));
                    log.info("用户 {} 离线，已自动退出队伍 {}", userId, teamupId);
                    // 人还开着页面（手动切成离线）时告诉他队伍为什么没了。
                    // 页面已经关掉的情况没人接，推一下也没有代价。
                    notify.push(userId, "teamup_left", Map.of(
                            "teamupId", teamupId,
                            "reason", "你已离线，已自动退出队伍"));
                } catch (Exception e) {
                    // 单支队伍失败不影响其他人，也不影响这批里剩下的队伍
                    log.warn("自动退出队伍 {} 失败（用户 {}）：{}", teamupId, userId, e.getMessage());
                }
            }
            // 退队的提示要查昵称，会把资料读回缓存 —— 而心跳超时那条路上，
            // 「置为离线」的 UPDATE 还在外层事务里没提交，读回来的就是旧的 ONLINE。
            // 这里再删一次，下一次读才会拿到真正的新状态。
            directory.evict(userId);
        } catch (Exception e) {
            log.error("离线自动退队失败，用户 {}", userId, e);
        }
    }

    /**
     * 现在还算不算「离线」。
     *
     * 两重判断：
     * 1. 数据库里的 status 必须还是 OFFLINE（用户可能已经又点回在线）；
     * 2. 在线索引里不能有他 —— 索引里有他说明页面已经重新报到，
     *    而数据库的 status 可能还被那条迟到的离线请求覆盖着（两个请求走不同连接，
     *    先后顺序没有保证）。只查数据库会在这种抢跑下误判。
     */
    private boolean stillOffline(Long userId) {
        String status = users.findById(userId).map(User::getStatus).orElse(null);
        if (!"OFFLINE".equals(status)) {
            return false;
        }
        Boolean alive = cache.zHas(CacheKeys.ONLINE, String.valueOf(userId));
        return !Boolean.TRUE.equals(alive);
    }
}
