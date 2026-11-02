package com.lol.lcu;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 从一场对局详情里提取"自己"的原始战绩，供上传使用。
 */
public final class MatchExtractor {

    private MatchExtractor() {
    }

    /** 提取自己在一场对局里的战绩；找不到自己时返回 null。 */
    public static SelfMatch extract(JsonNode game, String puuid, String name) {
        JsonNode self = findSelf(game, puuid, name);
        if (self == null) {
            return null;
        }
        JsonNode stats = self.path("stats");
        SelfMatch m = new SelfMatch();
        m.gameId = game.path("gameId").asLong();
        m.win = stats.path("win").asBoolean();
        m.lane = self.path("timeline").path("lane").asText();
        m.kills = stats.path("kills").asInt();
        m.deaths = stats.path("deaths").asInt();
        m.assists = stats.path("assists").asInt();
        m.cs = stats.path("totalMinionsKilled").asInt();
        m.visionScore = stats.path("visionScore").asInt();
        m.duration = game.path("gameDuration").asInt();
        m.playedAt = game.path("gameCreation").asLong(); // epoch 毫秒
        return m;
    }

    /**
     * 在 10 人参与数据里找到自己。
     * 国服是老版结构：participants 不含 puuid，玩家身份在 participantIdentities 里，
     * 通过 participantId 关联。
     */
    public static JsonNode findSelf(JsonNode game, String puuid, String name) {
        JsonNode participants = game.path("participants");

        // 1) 部分客户端 participants 里直接有 puuid
        if (participants.isArray()) {
            for (JsonNode p : participants) {
                if (puuid.equals(p.path("puuid").asText())) {
                    return p;
                }
            }
        }

        // 2) 国服：从 participantIdentities 按 puuid 或名字找到自己的 participantId
        JsonNode identities = game.path("participantIdentities");
        int selfId = -1;
        if (identities.isArray()) {
            for (JsonNode id : identities) {
                JsonNode player = id.path("player");
                boolean match = puuid.equals(player.path("puuid").asText())
                        || (!name.isBlank()
                                && (name.equals(player.path("summonerName").asText())
                                        || name.equals(player.path("gameName").asText())));
                if (match) {
                    selfId = id.path("participantId").asInt();
                    break;
                }
            }
        }

        if (selfId > 0 && participants.isArray()) {
            for (JsonNode p : participants) {
                if (p.path("participantId").asInt() == selfId) {
                    return p;
                }
            }
        }
        return null;
    }

    /** 一场对局里"自己"的原始战绩。 */
    public static final class SelfMatch {
        public long gameId;
        public boolean win;
        public String lane;
        public int kills;
        public int deaths;
        public int assists;
        public int cs;
        public int visionScore;
        public int duration;
        public long playedAt;
    }
}
