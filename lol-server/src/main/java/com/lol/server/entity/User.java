package com.lol.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 用户表。密码存的是 BCrypt 哈希，绝不存明文。
 */
@Entity
@Table(name = "t_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录账号，唯一 */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt 加密后的密码 */
    @Column(nullable = false, length = 100)
    private String password;

    /** 昵称（游戏内名字，交友展示用） */
    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 性别：男 / 女 / 保密 */
    @Column(length = 20)
    private String gender;

    /** 头像地址 */
    @Column(length = 255)
    private String avatar;

    /** 个性签名 */
    @Column(length = 200)
    private String bio;

    /**
     * 游戏客户端里的玩家唯一 id（puuid）。
     * 由「录入战绩」时从本机客户端读取后自动写入，不由用户填写 —— 它是把账号和
     * 游戏内身份对上的唯一可靠依据，手填会和战绩数据源不一致。
     */
    @Column(name = "puuid", length = 100)
    private String puuid;

    /** 召唤师名（游戏内昵称），同样是录入战绩时自动同步 */
    @Column(name = "summoner_name", length = 50)
    private String summonerName;

    /** 大区 */
    @Column(length = 30)
    private String region;

    /** 主玩位置 code：TOP/JUNGLE/MIDDLE/BOTTOM/SUPPORT */
    @Column(length = 20)
    private String position;

    /** 段位 code：IRON~CHALLENGER */
    @Column(name = "rank_tier", length = 20)
    private String rankTier;

    /** 在线状态：ONLINE/OFFLINE/BUSY */
    @Column(length = 20)
    private String status = "OFFLINE";

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    /** 综合实力分 0-100，按战绩评分计算 */
    @Column(name = "skill_score")
    private Integer skillScore;

    /** 实力等级 S/A/B/C/D */
    @Column(name = "skill_tier", length = 5)
    private String skillTier;

    /** 已录入并参与评分的对局数 */
    @Column(name = "match_count")
    private Integer matchCount = 0;

    public User() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getPuuid() {
        return puuid;
    }

    public void setPuuid(String puuid) {
        this.puuid = puuid;
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

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getRankTier() {
        return rankTier;
    }

    public void setRankTier(String rankTier) {
        this.rankTier = rankTier;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public Integer getSkillScore() {
        return skillScore;
    }

    public void setSkillScore(Integer skillScore) {
        this.skillScore = skillScore;
    }

    public String getSkillTier() {
        return skillTier;
    }

    public void setSkillTier(String skillTier) {
        this.skillTier = skillTier;
    }

    public Integer getMatchCount() {
        return matchCount;
    }

    public void setMatchCount(Integer matchCount) {
        this.matchCount = matchCount;
    }
}
