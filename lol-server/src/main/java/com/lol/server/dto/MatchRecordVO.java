package com.lol.server.dto;

import com.lol.server.entity.MatchRecord;

import java.time.LocalDateTime;

/**
 * 战绩展示对象。
 */
public class MatchRecordVO {

    private Long id;
    private Long gameId;
    private Boolean win;
    private String lane;
    private Integer kills;
    private Integer deaths;
    private Integer assists;
    private Double kda;
    private Integer cs;
    private Double csPerMin;
    private Integer visionScore;
    private Double score;
    private LocalDateTime playedAt;

    public static MatchRecordVO from(MatchRecord m) {
        MatchRecordVO vo = new MatchRecordVO();
        vo.setId(m.getId());
        vo.setGameId(m.getGameId());
        vo.setWin(m.getWin());
        vo.setLane(m.getLane());
        vo.setKills(m.getKills());
        vo.setDeaths(m.getDeaths());
        vo.setAssists(m.getAssists());

        int kills = m.getKills() == null ? 0 : m.getKills();
        int deaths = m.getDeaths() == null ? 0 : m.getDeaths();
        int assists = m.getAssists() == null ? 0 : m.getAssists();
        vo.setKda(deaths == 0 ? (double) (kills + assists) : (kills + assists) / (double) deaths);

        vo.setCs(m.getCs());
        int duration = m.getDuration() == null ? 0 : m.getDuration();
        int cs = m.getCs() == null ? 0 : m.getCs();
        vo.setCsPerMin(duration > 0 ? cs / (duration / 60.0) : 0);

        vo.setVisionScore(m.getVisionScore());
        vo.setScore(m.getScore());
        vo.setPlayedAt(m.getPlayedAt());
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Double getKda() {
        return kda;
    }

    public void setKda(Double kda) {
        this.kda = kda;
    }

    public Integer getCs() {
        return cs;
    }

    public void setCs(Integer cs) {
        this.cs = cs;
    }

    public Double getCsPerMin() {
        return csPerMin;
    }

    public void setCsPerMin(Double csPerMin) {
        this.csPerMin = csPerMin;
    }

    public Integer getVisionScore() {
        return visionScore;
    }

    public void setVisionScore(Integer visionScore) {
        this.visionScore = visionScore;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public LocalDateTime getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(LocalDateTime playedAt) {
        this.playedAt = playedAt;
    }
}
