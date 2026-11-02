package com.lol.common.result;

import java.util.List;

/**
 * 分页参数的边界处理。
 *
 * 页码和页大小是从 URL 上来的，一律当作不可信输入：
 * 传 0、传负数、传 10000 都要落到合理区间，不能让它们进到 SQL 里。
 * 三个列表接口共用这里，钳制口径才不会各自一套。
 */
public final class Pages {

    /** 前端一页 10 条，这里保持一致。 */
    public static final int DEFAULT_SIZE = 10;

    /** 上限 50：页再大也只是在拖慢查询，没有人真的看得完。 */
    public static final int MAX_SIZE = 50;

    private Pages() {
    }

    /** 页码从 1 起；不传或小于 1 都当第一页。 */
    public static int clampPage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    /** 页大小不传用 10，超出上限按上限截断。 */
    public static int clampSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** 在已经排好序的全量列表上切出第 page 页。供无法下推到 SQL 的场景（如 Redis 索引）使用。 */
    public static <T> List<T> slice(List<T> all, int page, int size) {
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }
}
