package com.lol.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 一条战绩记录：某用户在某一局游戏里的个人表现。
 */
@Entity
@Table(name = "t_match_record", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "game_id"})
})
public class MatchRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 归属用户 id */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** LCU 对局 id，配合 user_id 去重 */
    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(nullable = false)
    private Boolean win;

    /** 位置：TOP/JUNGLE/MIDDLE/BOTTOM/SUPPORT/UNKNOWN */
    @Column(length = 20)
    private String lane;

    @Column(nullable = false)
    private Integer kills;

    @Column(nullable = false)
    private Integer deaths;

    @Column(nullable = false)
    private Integer assists;

    /** 补刀数（totalMinionsKilled） */
    @Column(name = "cs")
    private Integer cs;

    @Column(name = "vision_score")
    private Integer visionScore;

    /** 对局时长（秒） */
    @Column(name = "duration")
    private Integer duration;

    /** 单场评分 0-100 */
    @Column(name = "score")
    private Double score;

    /** 对局发生时间 */
    @Column(name = "played_at")
    private LocalDateTime playedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
