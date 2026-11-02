package com.lol.server.constant;

import java.util.List;
import java.util.Set;

/**
 * 游戏相关枚举，与 lol-web/src/constants.js 和 API.md 保持一致。
 *
 * 原来这些集合在 ProfileService / TeamupService 里各存一份，改动时容易只改一处。
 */
public final class GameEnums {

    private GameEnums() {
    }

    public static final Set<String> POSITIONS = Set.of("TOP", "JUNGLE", "MIDDLE", "BOTTOM", "SUPPORT");

    /** 段位，顺序即强弱顺序（低 → 高）。 */
    public static final List<String> RANKS = List.of(
            "IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM",
            "EMERALD", "DIAMOND", "MASTER", "GRANDMASTER", "CHALLENGER");

    public static final Set<String> RANK_SET = Set.copyOf(RANKS);

    public static final Set<String> STATUS = Set.of("ONLINE", "OFFLINE", "BUSY");

    public static final Set<String> MODES = Set.of("RANKED", "NORMAL", "ARAM");

    /** 段位在阶梯上的位置，未知段位返回 -1。 */
    public static int rankIndex(String rankTier) {
        return rankTier == null ? -1 : RANKS.indexOf(rankTier);
    }
}
