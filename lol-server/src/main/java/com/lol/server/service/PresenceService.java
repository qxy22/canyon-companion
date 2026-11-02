package com.lol.server.service;

import com.lol.server.redis.CacheKeys;
import com.lol.server.redis.CacheService;
import com.lol.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「谁在线」这件事的 Redis 索引。
 *
 * 之前在线列表和匹配推荐都直接 userRepository.findAll() 把整张用户表捞出来，
 * 在内存里挑 status='ONLINE' 再排序。用户一多，每次刷新页面都要扫一遍全表，
 * 而里面绝大多数是根本不该出现的离线用户。
 *
 * 现在在线集合单独维护：一个 ZSET，member 是 userId，score 是最后活跃时间，
 * 天然按活跃度排好序。查在线列表变成「取前 N 个 member」，
 * 代价从 O(全部用户) 变成 O(在线人数)。
 *
 * 数据库里的 status 字段仍然是权威 —— Redis 只是它的派生索引。
 * 状态切换时先写 MySQL，成功后再维护索引；索引丢了能从库里重建，不会算错数据。
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    /**
     * 超过这么久没有心跳就算离线。前端 25 秒一次，等于允许连续丢三次；
     * 浏览器把后台标签页的定时器压到每分钟一次也不会误判。
     */
    static final long HEARTBEAT_TIMEOUT_MS = 90_000L;

    /** 扫描节奏。比超时短得多，用户关掉页面后最多 30 秒内就会从列表里消失。 */
    static final long SWEEP_INTERVAL_MS = 30_000L;

    /** 一轮最多处理这么多人，避免一次断网导致同一条 UPDATE 大到离谱。 */
    private static final long SWEEP_BATCH = 200;

    private final CacheService cache;
    private final UserDirectory directory;
    private final UserRepository userRepository;
    private final OfflineTeamupCleaner offlineCleaner;

    public PresenceService(CacheService cache, UserDirectory directory, UserRepository userRepository,
                           OfflineTeamupCleaner offlineCleaner) {
        this.cache = cache;
        this.directory = directory;
        this.userRepository = userRepository;
        this.offlineCleaner = offlineCleaner;
    }

    /**
     * 在线用户 id，最近活跃的排在前。返回 null 表示索引不可用，调用方须退回查数据库。
     *
     * 「集合为空」不等于「没人在线」——Redis 被清空或刚启动时就是这个样子。
     * 所以这里用 ONLINE_READY 标记区分，没标记就重建一次，打上标记，
     * 30 秒内不再重复做这件事（否则真的没人在线时，每次请求都要全表扫一遍）。
     */
    public List<Long> onlineIds() {
        Long size = cache.zSize(CacheKeys.ONLINE);
        if (size == null) {
            return null;   // Redis 连不上
        }
        if (size == 0 && !Boolean.TRUE.equals(cache.exists(CacheKeys.ONLINE_READY))) {
            rebuildFromDb();
        }
        List<Long> ids = cache.reverseRangeLongs(CacheKeys.ONLINE, 0, -1);
        return ids == null ? null : ids;
    }

    /** 在线用户 id，最多 limit 个。 */
    public List<Long> onlineIds(int limit) {
        List<Long> all = onlineIds();
        if (all == null) {
            return null;
        }
        return all.size() <= limit ? all : new ArrayList<>(all.subList(0, limit));
    }

    /**
     * 从数据库重建在线集合。
     *
     * 整批一次写入（而不是逐个 add），写完再核对条数，两样都过了才打「已就绪」标记。
     * 之前是逐个 add + 无条件打标记：中途任意一次写入被降级吞掉，剩下的就变成残缺名单，
     * 而且因为标记已经打了，之后 size 大于 0 就再也不会重建 —— 名单就永久缺一块。
     */
    private void rebuildFromDb() {
        List<Long> online = directory.rebuildOnlineIds();
        if (!online.isEmpty()) {
            Map<String, Double> members = new HashMap<>();
            long now = System.currentTimeMillis();
            for (Long id : online) {
                members.put(String.valueOf(id), (double) now);
            }

            Boolean written = cache.zAddAll(CacheKeys.ONLINE, members);
            Long actual = written != null && written ? cache.zSize(CacheKeys.ONLINE) : null;

            if (actual == null || actual < members.size()) {
                // 不打「已就绪」：下一个请求会再试一次，而不是把残缺名单当成品用下去
                log.warn("在线索引重建未完成（库里 {} 人，索引里 {} 人），稍后重试", members.size(), actual);
                return;
            }
            log.info("在线索引已从数据库重建：{} 人", members.size());
        }
        cache.mark(CacheKeys.ONLINE_READY, CacheKeys.ONLINE_READY_TTL_SECONDS);
    }

    /** 上线（或更新活跃时间）。 */
    public void markOnline(Long userId) {
        cache.zAdd(CacheKeys.ONLINE, String.valueOf(userId), System.currentTimeMillis());
    }

    /**
     * 心跳：只刷新活跃时间，**绝不改动状态**。
     *
     * 返回 false 表示这个人已经不在在线索引里了（被切成「游戏中」、被扫超时摘掉、
     * 或者 Redis 重启后重建时没有他）—— 前端据此决定要不要补一次「上线」。
     * 返回 null 表示 Redis 不可用，这时什么都不该做：既不知道该不该补，也不该改状态。
     */
    public Boolean touch(Long userId) {
        return cache.zTouch(CacheKeys.ONLINE, String.valueOf(userId), System.currentTimeMillis());
    }

    /**
     * 心跳超时兜底：页关掉时前端会主动报离线，但断网、进程被杀、笔记本合盖都不会。
     *
     * 没有这一步，那些"非正常离开"的人会永远挂在「在线可开黑」列表里 ——
     * 别人邀请他、等他开局，而他其实早就不在了。
     *
     * 每 30 秒扫一次，超过 90 秒没心跳就算离线（前端的节奏是 25 秒一次，
     * 等于允许连续丢三次；浏览器把后台标签页的定时器压到每分钟一次时也不会误判）。
     *
     * Redis 不可用时这一轮直接跳过：没有索引就没有"最近活跃时间"可依据，
     * 与其瞎猜，不如什么都不做 —— 这正是"Redis 是加速器不是数据持有人"的代价，
     * 可接受，因为 Redis 挂着的时候在线列表本来就走数据库兜底。
     */
    @Scheduled(fixedDelay = SWEEP_INTERVAL_MS, initialDelay = SWEEP_INTERVAL_MS)
    @Transactional
    public void sweepStale() {
        long cutoff = System.currentTimeMillis() - HEARTBEAT_TIMEOUT_MS;
        List<String> stale = cache.rangeByScore(CacheKeys.ONLINE, 0, cutoff, SWEEP_BATCH);
        if (stale == null || stale.isEmpty()) {
            return;
        }

        List<Long> ids = new ArrayList<>(stale.size());
        for (String s : stale) {
            try {
                ids.add(Long.valueOf(s));
            } catch (NumberFormatException ignored) {
                // 脏成员直接忽略，下一轮还会被扫到，不耽误别人
            }
        }
        cache.zRemove(CacheKeys.ONLINE, stale);
        if (ids.isEmpty()) {
            return;
        }

        int changed = userRepository.markOffline(ids);
        // 资料有缓存，状态改了必须让缓存失效，否则列表里还显示「在线」
        ids.forEach(directory::evict);
        log.info("心跳超时：索引里摘掉 {} 人，其中 {} 人由在线改为离线", stale.size(), changed);

        // 超时的人跟关掉页面是一回事，队伍也得退。这里不用等宽限期 ——
        // 90 秒没心跳已经是「确认离开」，再等几秒只是让队友多等。
        // 清理放在独立事务里（REQUIRES_NEW），单支队伍退不掉不会把上面那次
        // 批量置离线一起回滚。
        ids.forEach(offlineCleaner::exitNow);
    }

    /** 下线 / 忙碌：不在「在线可开黑」里出现，从索引里摘掉。 */
    public void remove(Long userId) {
        cache.zRemove(CacheKeys.ONLINE, List.of(String.valueOf(userId)));
    }

    /** 在线人数。没连上 Redis 时返回 null，别把它当 0 用。 */
    public Long onlineCount() {
        return cache.zSize(CacheKeys.ONLINE);
    }

    /**
     * 启动时把索引建起来。
     *
     * 这里刻意不跳过重建：以前的做法是「集合里已经有数据就跳过」，听起来省事，
     * 但它让任何一次残缺写入都无法自愈 —— 只要被理解没人会再把它补回来（升级版本除外）。
     * 重建对幂等友好（ZADD 对已有成员只是更新），且只在启动时做一次，全表扫描的代价可以接受。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void rebuildOnStartup() {
        if (cache.zSize(CacheKeys.ONLINE) == null) {
            log.warn("Redis 连不上，在线列表将走数据库查询 —— 功能不受影响，只是慢一点");
            return;
        }
        rebuildFromDb();
    }

    /** 供排查用：把当前索引里的 id 原样返回。 */
    public List<Long> snapshot() {
        List<Long> ids = onlineIds();
        return ids == null ? new ArrayList<>() : ids;
    }
}
