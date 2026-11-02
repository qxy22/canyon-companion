package com.lol.lcu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 通过 LCU API 读取当前登录账号的战绩。
 *
 * 国服注意点：
 *  - 新版接口走 GAMHS 路由 /lol-match-history/v1/products/lol/{puuid}/matches；
 *  - 列表接口只返回玩家自己一个人的数据，要拿完整 10 人阵容需逐场调用
 *    /lol-match-history/v1/games/{gameId}。
 */
public final class MatchHistoryReader {

    private final LcuClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public MatchHistoryReader(LcuClient client) {
        this.client = client;
    }

    /** 当前登录的召唤师信息。 */
    public Summoner currentSummoner() throws IOException, InterruptedException {
        JsonNode node = mapper.readTree(client.get("/lol-summoner/v1/current-summoner"));
        String puuid = node.path("puuid").asText();
        String name = node.path("gameName").asText(); // 国服名字在 gameName，displayName 为空
        String tagLine = node.path("tagLine").asText();
        long level = node.path("summonerLevel").asLong();
        return new Summoner(puuid, name, tagLine, level);
    }

    /** 最近 count 场的 gameId 列表。 */
    public List<Long> matchIds(String puuid, int count) throws IOException, InterruptedException {
        String path = "/lol-match-history/v1/products/lol/" + puuid
                + "/matches?begIndex=0&endIndex=" + count;
        JsonNode node = mapper.readTree(client.get(path));

        List<Long> ids = new ArrayList<>();
        JsonNode games = node.path("games").path("games");
        if (games.isArray()) {
            for (JsonNode g : games) {
                long id = g.path("gameId").asLong();
                if (id != 0) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    /** 单场完整对局详情（含 10 人参与者）。 */
    public JsonNode gameDetail(long gameId) throws IOException, InterruptedException {
        return mapper.readTree(client.get("/lol-match-history/v1/games/" + gameId));
    }

    /** 召唤师简信息。 */
    public record Summoner(String puuid, String name, String tagLine, long level) {
    }
}
