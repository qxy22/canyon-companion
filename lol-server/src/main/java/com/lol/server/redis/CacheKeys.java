package com.lol.server.redis;

/**
 * 缓存键的集中定义。
 *
 * 散落在各个 Service 里写字符串 Redis key，改动时很容易漏掉某一处失效，
 * 于是同一个数据就出现两份不一致的缓存。所有 key 在这里定死：
 * 哪里写、哪里删，一眼能对上。
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    /** 所有 key 的前缀。答辩演示时想看缓存里有什么，`KEYS lol:*` 一次看全。 */
    public static final String NS = "lol:";

    // ---------------------------------------------------------------- 用户

    /** 单个用户的公开资料 JSON。命中率最高的一层：几乎所有列表都要用它。 */
    public static String user(Long userId) {
        return NS + "user:" + userId;
    }

    /** 在线／可发现用户的有序集合：member=userId，score=最后活跃时间（毫秒）。 */
    public static final String ONLINE = NS + "online";

    /**
     * 「在线索引已经初始化过」的标记。
     *
     * 没有它就无法区分两种情况：Redis 明明活着，但集合确实是空的。
     * 「真的没人在线」和「还没来得及建索引」看起来一样，前者不能触发全表扫描，
     * 后者必须触发，否则用户会发现在线列表一直是空的。
     */
    public static final String ONLINE_READY = NS + "online:ready";
    /** 标记多久失效一次。到期后重新确认一次，防止 Redis 被清空后一直不重建。 */
    public static final long ONLINE_READY_TTL_SECONDS = 30;

    // ---------------------------------------------------------------- 组队

    /** 某支队伍的成员 id 集合。聊天每来一条消息都要判一次"他还在不在队里"。 */
    public static String teamupMembers(Long teamupId) {
        return NS + "teamup:members:" + teamupId;
    }

    /** 房间里最新一条消息的 id。算未读数时不再每次都 COUNT 前先 MAX 一次。 */
    public static String teamupLastId(Long teamupId) {
        return NS + "teamup:lastid:" + teamupId;
    }

    // ---------------------------------------------------------------- 有效期

    /** 用户资料：改资料的地方都会显式删 key，TTL 只是兜底（防漏删导致长期脏数据）。 */
    public static final long USER_TTL_SECONDS = 30 * 60;

    /** 队伍成员：写操作必删，TTL 兜底。 */
    public static final long MEMBERS_TTL_SECONDS = 30 * 60;

    /** 房间最新消息 id：消息是不停增长的，这个 key 必须短命。 */
    public static final long LAST_ID_TTL_SECONDS = 60;
}
