package com.lol.server.dto;

/**
 * 一次「从本机客户端录入」的结果。
 *
 * 除了更新后的资料，还要说清这次具体同步到了什么：段位是可能同步失败的
 * （账号未定级、排位未开放），如果不告诉用户，他会以为功能坏了。
 */
public class LcuSyncResult {

    private UserProfileVO profile;

    /** 本次从客户端读到的对局数（重复的对局会被跳过，所以不一定等于新增数） */
    private Integer fetchedCount;

    /** 从客户端读到的召唤师名 */
    private String summonerName;

    /** 段位是否成功同步 */
    private Boolean rankSynced;

    /** 段位 code，如 GOLD */
    private String rankTier;

    /** 罗马数字段位，如 II；大师以上通常为空 */
    private String rankDivision;

    /** 胜点 */
    private Integer leaguePoints;

    /** 需要额外说明的情况；正常时为 null */
    private String note;

    public UserProfileVO getProfile() {
        return profile;
    }

    public void setProfile(UserProfileVO profile) {
        this.profile = profile;
    }

    public Integer getFetchedCount() {
        return fetchedCount;
    }

    public void setFetchedCount(Integer fetchedCount) {
        this.fetchedCount = fetchedCount;
    }

    public String getSummonerName() {
        return summonerName;
    }

    public void setSummonerName(String summonerName) {
        this.summonerName = summonerName;
    }

    public Boolean getRankSynced() {
        return rankSynced;
    }

    public void setRankSynced(Boolean rankSynced) {
        this.rankSynced = rankSynced;
    }

    public String getRankTier() {
        return rankTier;
    }

    public void setRankTier(String rankTier) {
        this.rankTier = rankTier;
    }

    public String getRankDivision() {
        return rankDivision;
    }

    public void setRankDivision(String rankDivision) {
        this.rankDivision = rankDivision;
    }

    public Integer getLeaguePoints() {
        return leaguePoints;
    }

    public void setLeaguePoints(Integer leaguePoints) {
        this.leaguePoints = leaguePoints;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
