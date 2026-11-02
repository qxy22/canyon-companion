package com.lol.server.dto;

import java.util.List;

/**
 * 战绩导入请求体。
 */
public class MatchImportRequest {

    /** 游戏客户端里的玩家唯一 id。由 LCU 读取后传入，是这个账号与游戏内身份的唯一对应依据。 */
    private String puuid;

    /** 召唤师名（可带 # 后缀）。由 LCU 读取后传入，总是覆盖本地值。 */
    private String summonerName;

    /** 段位 code。由 LCU 读取后传入，总是覆盖本地值。 */
    private String rankTier;

    /** 大区，用户资料为空时自动回填 */
    private String region;

    private List<MatchImportItem> matches;

    public String getPuuid() {
        return puuid;
    }

    public void setPuuid(String puuid) {
        this.puuid = puuid;
    }

    public String getRankTier() {
        return rankTier;
    }

    public void setRankTier(String rankTier) {
        this.rankTier = rankTier;
    }

    public String getSummonerName() {
        return summonerName;
    }

    public void setSummonerName(String summonerName) {
        this.summonerName = summonerName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public List<MatchImportItem> getMatches() {
        return matches;
    }

    public void setMatches(List<MatchImportItem> matches) {
        this.matches = matches;
    }
}
