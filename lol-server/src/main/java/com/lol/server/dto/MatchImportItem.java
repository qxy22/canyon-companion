package com.lol.server.dto;

/**
 * 单场战绩导入项（由本地读取器提取并上传）。
 */
public class MatchImportItem {

    private Long gameId;
    private Boolean win;
    private String lane;
    private Integer kills;
    private Integer deaths;
    private Integer assists;
    private Integer cs;
    private Integer visionScore;
    private Integer duration;

    /** 对局发生时间（epoch 毫秒） */
    private Long playedAt;

    public Long getGameId() {
        return gameId;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public Boolean getWin() {
        return win;
    }

    public void setWin(Boolean win) {
        this.win = win;
    }

    public String getLane() {
        return lane;
    }

    public void setLane(String lane) {
        this.lane = lane;
    }

    public Integer getKills() {
        return kills;
    }

    public void setKills(Integer kills) {
        this.kills = kills;
    }

    public Integer getDeaths() {
        return deaths;
    }

    public void setDeaths(Integer deaths) {
        this.deaths = deaths;
    }

    public Integer getAssists() {
        return assists;
    }

    public void setAssists(Integer assists) {
        this.assists = assists;
    }

    public Integer getCs() {
        return cs;
    }

    public void setCs(Integer cs) {
        this.cs = cs;
    }

    public Integer getVisionScore() {
        return visionScore;
    }

    public void setVisionScore(Integer visionScore) {
        this.visionScore = visionScore;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Long getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(Long playedAt) {
        this.playedAt = playedAt;
    }
}
