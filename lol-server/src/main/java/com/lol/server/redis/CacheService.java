package com.lol.server.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Redis 读写的唯一出口。
 *
 * 一个硬性要求：**Redis 挂了服务必须照常能用**。
 * 缓存是加速器，不是数据的持有人 —— 真正的数据在 MySQL 里。
 * 所以这里每个方法都自己吞掉连接异常并返回「没拿到」的结果，
 * 调用方看到空值就会退回查数据库。答辩现场 Redis 没起、或者中途崩了，
 * 最坏的结果是慢一点，而不是整个系统 500。
 *
 * 相反方向的风险要注意：读失败在这里是「未命中」，写失败也是静默忽略。
 * 绝不能出现「写成功了删缓存失败，于是缓存里留下旧数据」——
 * 那样用户会改不动自己的资料。所以失效操作一律放在数据库写成功之后，
 * 并且写失败不影响请求本身成功。
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    /** Redis 连不上时不再反复重试的连接冷静期，避免每个请求都卡一次超时 */
    private volatile long unavailableUntil = 0;
    private static final long COOLDOWN_MS = 5_000;

    public CacheService(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- 单个 JSON 对象

    /** 取一个对象，反序列化的具体类型由调用方给（UserProfileVO 等）。 */
    public <T> T get(String key, TypeReference<T> type) {
        String raw = silence(() -> redis.opsForValue().get(key), key, "get");
        if (raw == null) {
            return null;
        }
        try {
            return mapper.readValue(raw, type);
        } catch (Exception e) {
            // 缓存里的 JSON 解析不了（可能是旧版本遗留的结构），删掉走数据库，
            // 不要让它变成永久解析错误
            log.warn("缓存 {} 内容无法解析，已丢弃：{}", key, e.getMessage());
            silence(() -> redis.delete(key), key, "delete-corrupted");
            return null;
        }
    }

    /** 写一个对象，带 TTL。 */
    public void set(String key, Object value, long ttlSeconds) {
        try {
            String raw = mapper.writeValueAsString(value);
            silence(() -> redis.opsForValue().set(key, raw, ttlSeconds, TimeUnit.SECONDS), key, "set");
        } catch (Exception e) {
            log.warn("序列化缓存 {} 失败：{}", key, e.getMessage());
        }
    }

    /** 删一个 key。所有「数据变了」的地方第一时间调它。 */
    public void delete(String key) {
        silence(() -> redis.delete(key), key, "delete");
    }

    /** key 是否存在。用来区分「确实没有人」和「索引还没建过」。 */
    public Boolean exists(String key) {
        return silence(() -> redis.hasKey(key), key, "exists");
    }

    /** 打一个短期存在的标记。 */
    public void mark(String key, long ttlSeconds) {
        silence(() -> redis.opsForValue().set(key, "1", ttlSeconds, TimeUnit.SECONDS), key, "mark");
    }

    public void deleteAll(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        silence(() -> redis.delete(new ArrayList<>(keys)), "batch", "delete");
    }

    // ---------------------------------------------------------------- 批量

    /**
     * 一次网络往返取回一批对象。
     * MGET 是关键：列表页要的是几十个人的资料，逐个 GET 会把这次省下的时间
     * 全赔进网络往返里。
     *
     * @param loader 未命中的 key 对应的回源函数（批量查数据库），返回缺失的那部分
     */
    public <T> Map<String, T> multiGet(Collection<String> keys, TypeReference<T> type,
                                       Function<List<String>, Map<String, T>> loader) {
        if (keys == null || keys.isEmpty()) {
            return new HashMap<>();
        }
        List<String> keyList = new ArrayList<>(keys);

        List<String> values = silence(() -> redis.opsForValue().multiGet(keyList), "batch", "mget");
        Map<String, T> result = new HashMap<>();
        List<String> missed = new ArrayList<>();
        for (int i = 0; i < keyList.size(); i++) {
            String raw = values == null ? null : (i < values.size() ? values.get(i) : null);
            if (raw == null) {
                missed.add(keyList.get(i));
                continue;
            }
            try {
                result.put(keyList.get(i), mapper.readValue(raw, type));
            } catch (Exception e) {
                missed.add(keyList.get(i));
            }
        }
        if (!missed.isEmpty() && loader != null) {
            Map<String, T> loaded = loader.apply(missed);
            if (loaded != null) {
                result.putAll(loaded);
            }
        }
        return result;
    }

    /** 批量写，给回源后的回填用。 */
    public void multiSet(Map<String, Object> payloads, long ttlSeconds) {
        if (payloads == null || payloads.isEmpty()) {
            return;
        }
        Map<String, String> raw = new HashMap<>();
        for (Map.Entry<String, Object> e : payloads.entrySet()) {
            try {
                raw.put(e.getKey(), mapper.writeValueAsString(e.getValue()));
            } catch (Exception ignored) {
                // 单个序列化失败不影响其余的
            }
        }
        if (!raw.isEmpty()) {
            silence(() -> redis.opsForValue().multiSet(raw), "batch", "mset");
        }
        // multiSet 不带 TTL，逐个补有效期
        for (String key : raw.keySet()) {
            silence(() -> redis.expire(key, ttlSeconds, TimeUnit.SECONDS), key, "expire");
        }
    }

    // ---------------------------------------------------------------- 集合

    /** 集合成员。返回 null 表示没拿到（key 不存在也算没拿到，调用方会查库重建）。 */
    public Set<String> members(String key) {
        Set<String> got = silence(() -> redis.opsForSet().members(key), key, "smembers");
        return got == null || got.isEmpty() ? null : got;
    }

    public void addToSet(String key, Collection<String> values, long ttlSeconds) {
        silence(() -> {
            redis.opsForSet().add(key, values.toArray(new String[0]));
            redis.expire(key, ttlSeconds, TimeUnit.SECONDS);
            return true;
        }, key, "sadd");
    }

    /**
     * 判断一个成员在不在集合里。
     * 缓存拿不到（索引还没建 / Redis 不可用）时返回 null，由调用方查库 ——
     * 返回 false 会把合法用户挡在外面，这个区别很要命。
     */
    public Boolean isMember(String key, String value) {
        Boolean membership = silence(() -> redis.opsForSet().isMember(key, value), key, "sismember");
        if (membership == null || Boolean.TRUE.equals(membership)) {
            return membership;
        }
        // Redis 对「集合中确实没有这个人」和「这个集合压根还没建过」都返回 false。
        // 必须分开：把没建过索引当成不是成员，会把刚发布队伍的队长挡在自己的房间外，
        // 而且之后再也不会有人把索引建起来（因为回源的那步永远走不到）。
        // 多这一次 EXISTS 只发生在 false 的分支上，true 依旧是一次往返。
        Boolean built = exists(key);
        if (built == null || Boolean.FALSE.equals(built)) {
            return null;
        }
        return Boolean.FALSE;
    }

    // ---------------------------------------------------------------- 有序集合

    /** 按 score 倒序取 top N。返回 null 表示没拿到。 */
    public List<String> reverseRange(String key, long start, long end) {
        Set<String> got = silence(
                () -> redis.opsForZSet().reverseRange(key, start, end), key, "zrevrange");
        return got == null ? null : new ArrayList<>(got);
    }

    /** 一次取全部并倒序 converts 成 id 列表，在线人数不多，量级可控。 */
    public List<Long> reverseRangeLongs(String key, long start, long end) {
        Set<String> got = silence(
                () -> redis.opsForZSet().reverseRange(key, start, end), key, "zrevrange");
        if (got == null) {
            return null;
        }
        List<Long> ids = new ArrayList<>(got.size());
        for (String s : got) {
            try {
                ids.add(Long.valueOf(s));
            } catch (NumberFormatException ignored) {
                // 脏数据跳过，不让它传染后面的查询
            }
        }
        return ids;
    }

    public void zAdd(String key, String member, double score) {
        silence(() -> redis.opsForZSet().add(key, member, score), key, "zadd");
    }

    /**
     * 一次写进整批成员。单个成员逐个 add 做不到「要么全成，要么全不成」——
     * 中途任何一次失败都会留下一个残缺的集合，而调用方看不出来。
     * ZADD 支持一次带多个 member，整条命令在 Redis 里是原子的。
     *
     * 返回值只说明「命令有没有成功执行」，不说明新增了几条：
     * 成员已存在时 Redis 返回的是 0（只是把 score 覆盖了），那不算失败。
     */
    public Boolean zAddAll(String key, Map<String, Double> members) {
        if (members == null || members.isEmpty()) {
            return Boolean.TRUE;
        }
        Set<TypedTuple<String>> tuples = new java.util.HashSet<>();
        members.forEach((member, score) -> tuples.add(TypedTuple.of(member, score)));
        Long added = silence(() -> redis.opsForZSet().add(key, tuples), key, "zadd-batch");
        return added != null;
    }

    /**
     * 只刷新已有成员的 score（等价于 Redis 的 ZADD XX），不新增成员。
     *
     * 心跳必须用它而不是普通 zAdd：被手动切成「游戏中」或「离线」的人早就被摘出了在线索引，
     * 普通 zAdd 会把他们悄悄塞回「在线可开黑」，而数据库里明明不是 ONLINE。
     *
     * 这里刻意分成「先查再写」两步，而不是拿 ZADD 的返回值判断存在性 ——
     * ZADD（以及 Spring 的 add()）返回的是**新增了几个成员**，只更新分数时恒为 false/0，
     * 拿它当"在不在"会得出「明明是刚上线的人，心跳却说他不在」这种结论。
     *
     * 返回 null = Redis 不可用（别当成"不在索引里"）；false = 这个成员本来就不在；
     * true = 刷新成功。
     */
    public Boolean zTouch(String key, String member, double score) {
        return silence(() -> {
            if (redis.opsForZSet().score(key, member) == null) {
                return Boolean.FALSE;
            }
            redis.opsForZSet().add(key, member, score);
            return Boolean.TRUE;
        }, key, "zadd-xx");
    }

    /**
     * 某个成员在不在有序集合里。
     *
     * 用来回答「这个人的页面现在还开着吗」—— 离线退队前的最后一道确认。
     * 返回 null 表示 Redis 不可用（这时只能靠数据库里的 status 判断，别把它当 false）。
     */
    public Boolean zHas(String key, String member) {
        return silence(() -> redis.opsForZSet().score(key, member) != null, key, "zscore");
    }

    /**
     * 按 score 区间取成员，给「心跳超时」扫描用。
     * 返回 null 表示没拿到（Redis 挂了）；空列表表示这一段里确实一个人都没有 ——
     * 两者必须分开，否则 Redis 一挂就会把全站用户当成超时下线。
     */
    public List<String> rangeByScore(String key, double min, double max, long limit) {
        Set<String> got = silence(
                () -> redis.opsForZSet().rangeByScore(key, min, max, 0, limit), key, "zrangebyscore");
        return got == null ? null : new ArrayList<>(got);
    }

    public void zRemove(String key, Collection<String> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        silence(() -> redis.opsForZSet().remove(key, members.toArray(new Object[0])), key, "zrem");
    }

    /** 在线人数。用来快速判断索引是否还健全。 */
    public Long zSize(String key) {
        return silence(() -> redis.opsForZSet().size(key), key, "zcard");
    }

    /** 一次网络往返判断多个成员是否在集合里（批量聊天成员校验时用得上）。 */
    public Map<String, Boolean> multiIsMember(String key, Collection<String> values) {
        List<Object> flags = silence(() -> redis.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            StringRedisConnection conn = (StringRedisConnection) connection;
            for (String v : values) {
                conn.sIsMember(key, v);
            }
            return null;
        }), key, "pipeline-sismember");
        if (flags == null || flags.size() != values.size()) {
            return Collections.emptyMap();
        }
        Map<String, Boolean> out = new HashMap<>();
        int i = 0;
        for (String v : values) {
            Object f = flags.get(i++);
            out.put(v, Boolean.TRUE.equals(f));
        }
        return out;
    }

    // ---------------------------------------------------------------- 内部

    /**
     * 所有 Redis 调用都从这里过：连不上就返回 null，并在一小段时间内不再重试。
     * 冷静期是必要的 —— 否则 Redis 一挂，每个请求都要等满连接超时，
     * 服务会被自己拖死，比不缓存还慢。
     */
    private <T> T silence(java.util.function.Supplier<T> action, String key, String op) {
        if (System.currentTimeMillis() < unavailableUntil) {
            return null;
        }
        try {
            return action.get();
        } catch (Exception e) {
            unavailableUntil = System.currentTimeMillis() + COOLDOWN_MS;
            log.warn("Redis 不可用（{} {}），已降级到数据库：{}", op, key, e.getMessage());
            return null;
        }
    }

    /**
     * 没有返回值的那个版本。Redis 的写操作大多是 void（set / multiSet / expire），
     * 套不进 Supplier 里，但同样要在连不上时静默，别把异常抛给业务。
     */
    private void silence(Runnable action, String key, String op) {
        if (System.currentTimeMillis() < unavailableUntil) {
            return;
        }
        try {
            action.run();
        } catch (Exception e) {
            unavailableUntil = System.currentTimeMillis() + COOLDOWN_MS;
            log.warn("Redis 不可用（{} {}），已降级到数据库：{}", op, key, e.getMessage());
        }
    }

    /** 供排查用：缓存现在是否能用。 */
    public boolean available() {
        try {
            String pong = redis.getConnectionFactory().getConnection().ping();
            return "PONG".equals(pong);
        } catch (Exception e) {
            return false;
        }
    }
}
