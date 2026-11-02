package com.lol.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lol.server.dto.UserProfileVO;
import com.lol.server.entity.User;
import com.lol.server.redis.CacheKeys;
import com.lol.server.redis.CacheService;
import com.lol.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户资料的统一入口。
 *
 * 之前每个 Service 都自己 userRepository.findAllById(...) 查一遍：
 * - 招募大厅要查一遍所有成员
 * - 聊天历史每条消息要查一遍发送者
 * - 每条聊天消息要为「发送者自己」再查一遍
 * 同一批用户的资料在一次页面加载里被反复读，这是数据库压力的主要来源。
 *
 * 现在统一从这里走：先问 Redis，缺的回源一次 MySQL 并回填。
 * 谁改了资料，谁负责调 {@link #evict}，否则会出现改完了页面上还是旧的。
 */
@Service
public class UserDirectory {

    private static final Logger log = LoggerFactory.getLogger(UserDirectory.class);

    private static final TypeReference<UserProfileVO> TYPE = new TypeReference<UserProfileVO>() {
    };

    private final UserRepository userRepository;
    private final CacheService cache;

    public UserDirectory(UserRepository userRepository, CacheService cache) {
        this.userRepository = userRepository;
        this.cache = cache;
    }

    // ---------------------------------------------------------------- 读

    public UserProfileVO profile(Long id) {
        if (id == null) {
            return null;
        }
        UserProfileVO hit = cache.get(CacheKeys.user(id), TYPE);
        if (hit != null) {
            return hit;
        }
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return null;
        }
        UserProfileVO vo = UserProfileVO.from(user);
        cache.set(CacheKeys.user(id), vo, CacheKeys.USER_TTL_SECONDS);
        return vo;
    }

    /**
     * 批量取资料，保持入参顺序。
     * 一次 MGET 拿到所有命中的，回源的也只有没命中的那几个 ——
     * 换成逐个 profile() 调用，网络往返数会等于人数。
     */
    public Map<Long, UserProfileVO> profiles(Collection<Long> ids) {
        Map<Long, UserProfileVO> out = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        List<Long> wanted = ids.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (wanted.isEmpty()) {
            return out;
        }

        List<String> keys = wanted.stream().map(CacheKeys::user).collect(Collectors.toList());
        Map<String, UserProfileVO> got = cache.multiGet(keys, TYPE, missing -> {
            List<Long> missingIds = missing.stream().map(UserDirectory::idOfKey).collect(Collectors.toList());
            // 缺多少查多少，一次 findAllById 搞定，顺手回填缓存
            Map<Long, UserProfileVO> fresh = new LinkedHashMap<>();
            for (User u : userRepository.findAllById(missingIds)) {
                fresh.put(u.getId(), UserProfileVO.from(u));
            }
            if (!fresh.isEmpty()) {
                Map<String, Object> refill = new LinkedHashMap<>();
                fresh.forEach((uid, vo) -> refill.put(CacheKeys.user(uid), vo));
                cache.multiSet(refill, CacheKeys.USER_TTL_SECONDS);
            }
            Map<String, UserProfileVO> typed = new LinkedHashMap<>();
            fresh.forEach((uid, vo) -> typed.put(CacheKeys.user(uid), vo));
            return typed;
        });

        // multiGet 返回的是 key → VO，换回 id → VO，再按入参顺序输出
        Map<Long, UserProfileVO> byId = new LinkedHashMap<>();
        got.forEach((k, v) -> byId.put(idOfKey(k), v));
        for (Long id : wanted) {
            UserProfileVO vo = byId.get(id);
            if (vo != null) {
                out.put(id, vo);
            }
        }
        return out;
    }

    /** 从 `lol:user:123` 里取出 123。key 的拼法变了这里会一起炸，比散落的字符串安全。 */
    private static Long idOfKey(String key) {
        return Long.valueOf(key.substring(key.lastIndexOf(':') + 1));
    }

    // ---------------------------------------------------------------- 失效

    /** 资料改了必须调它。传串口多一点没事，漏了才会出问题。 */
    public void evict(Long id) {
        if (id != null) {
            cache.delete(CacheKeys.user(id));
        }
    }

    public void evictAll(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<String> keys = ids.stream().filter(java.util.Objects::nonNull)
                .map(CacheKeys::user).distinct().collect(Collectors.toList());
        cache.deleteAll(keys);
    }

    /**
     * 按 id 直接拿实体。
     * 只在确实要写文件的场合用（比如校验 / 保存），展示一律走 {@link #profile}。
     */
    public User entity(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    /** 缓存健康自检，排查演示环境问题时用。 */
    public boolean cacheAvailable() {
        return cache.available();
    }

    /**
     * 启动时把在线用户从数据库重建到 Redis。
     * 只有进程重启、Redis 又恰好被清空的场景会用到 —— 平时都跟着状态变更增量维护。
     */
    public List<Long> rebuildOnlineIds() {
        List<Long> ids = new ArrayList<>();
        for (User u : userRepository.findAll()) {
            if ("ONLINE".equals(u.getStatus())) {
                ids.add(u.getId());
            }
        }
        return ids;
    }
}
