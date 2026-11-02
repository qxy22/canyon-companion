package com.lol.lcu;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 演示入口：连接本地 LCU -> 读最近 10 场 -> 提取自身数据 -> 算示例实力分。
 */
public final class Main {

    public static void main(String[] args) {
        try {
            LcuCredentials creds = CredentialFinder.discover();
            System.out.println("已连接 LCU，端口 " + creds.port());

            LcuClient client = new LcuClient(creds);
            MatchHistoryReader reader = new MatchHistoryReader(client);

            MatchHistoryReader.Summoner me = reader.currentSummoner();
            System.out.println("召唤师: " + me.name() + "#" + me.tagLine() + "（等级 " + me.level() + "）");

            List<Long> ids = reader.matchIds(me.puuid(), 10);
            System.out.println("最近对局数: " + ids.size());
            System.out.println();

            double total = 0;
            int analyzed = 0;
            for (long id : ids) {
                JsonNode game = reader.gameDetail(id);
                JsonNode self = MatchExtractor.findSelf(game, me.puuid(), me.name());
                if (self == null) {
                    continue;
                }

                JsonNode stats = self.path("stats");
                int kills = stats.path("kills").asInt();
                int deaths = stats.path("deaths").asInt();
                int assists = stats.path("assists").asInt();
                boolean win = stats.path("win").asBoolean();
                int cs = stats.path("totalMinionsKilled").asInt();
                int vision = stats.path("visionScore").asInt();
                int duration = game.path("gameDuration").asInt();
                String lane = self.path("timeline").path("lane").asText();

                double kda = deaths == 0 ? kills + assists : (kills + assists) / (double) deaths;
                double csPerMin = duration > 0 ? cs / (duration / 60.0) : 0;
                double score = score(kda, csPerMin, win);

                System.out.printf(
                        "%s | %-7s | K=%d D=%d A=%d | KDA=%.2f | CS/分=%.1f | 视野=%d | 评分=%.0f%n",
                        win ? "胜" : "负", lane, kills, deaths, assists, kda, csPerMin, vision, score);

                total += score;
                analyzed++;
            }

            if (analyzed > 0) {
                System.out.printf("%n最近 %d 场平均实力评分: %.0f / 100%n", analyzed, total / analyzed);
            } else {
                System.out.println("没有解析到对局数据。");
            }
        } catch (Exception e) {
            System.err.println("出错了: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 示例评分：KDA + 补刀 + 胜负。
     * 这只是起点，实际应加入位置归一化、参团率、视野分、对手强度等维度。
     */
    private static double score(double kda, double csPerMin, boolean win) {
        double kdaScore = Math.min(100, kda / 5.0 * 100);       // KDA=5 记满分
        double csScore = Math.min(100, csPerMin / 8.0 * 100);   // 8 补刀/分记满分
        double winScore = win ? 100 : 30;
        return 0.5 * kdaScore + 0.25 * csScore + 0.25 * winScore;
    }
}
