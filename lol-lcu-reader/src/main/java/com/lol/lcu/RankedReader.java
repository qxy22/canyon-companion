package com.lol.lcu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 读取当前登录账号的排位段位。
 *
 * 国服接口：/lol-ranked/v1/current-ranked-stats，返回的 queueMap 按队列分组，
 * tier 形如 GOLD / PLATINUM，rank 是罗马数字段位（I~IV），leaguePoints 是胜点。
 */
public final class RankedReader {

    /** 优先取单双排，它最能代表个人水平；其次是灵活组排。 */
    private static final List<String> QUEUE_PRIORITY = List.of("RANKED_SOLO_5x5", "RANKED_FLEX_SR");

    private final LcuClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public RankedReader(LcuClient client) {
        this.client = client;
    }

    /**
     * 当前账号的段位。
     *
     * 未定级、接口不存在或客户端未开放排位时返回 null：段位不是录入战绩的必要条件，
     * 不能因为读不到就让整次同步失败。
     */
    public Rank currentRank() {
        JsonNode node;
        try {
            node = mapper.readTree(client.get("/lol-ranked/v1/current-ranked-stats"));
        } catch (Exception e) {
            return null;
        }

        JsonNode queueMap = node.path("queueMap");
        for (String queue : QUEUE_PRIORITY) {
            JsonNode q = queueMap.path(queue);
            if (q.isMissingNode()) {
                continue;
            }
            String tier = q.path("tier").asText(null);
            if (tier == null || tier.isBlank() || "NONE".equalsIgnoreCase(tier)) {
                continue;
            }
            return new Rank(
                    tier.trim().toUpperCase(),
                    q.path("rank").asText(""),
                    q.path("leaguePoints").asInt(0),
                    queue);
        }
        return null;
    }

    /** 段位信息。division 为罗马数字段位（I~IV），大师以上通常为空。 */
    public record Rank(String tier, String division, int leaguePoints, String queue) {
    }
}
