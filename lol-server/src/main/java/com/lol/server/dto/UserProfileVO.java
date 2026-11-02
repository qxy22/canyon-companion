package com.lol.server.dto;

import com.lol.server.entity.User;

import java.time.LocalDateTime;

/**
 * 交友资料（用于资料页、搜索、在线列表、推荐、组队等展示，不含密码和登录账号）。
 */
public class UserProfileVO {

    private Long id;
    private String nickname;
    private String gender;
    private String avatar;
    private String bio;
    private String puuid;
    private String summonerName;
    private String region;
    private String position;
    private String rankTier;
    private String status;
    private LocalDateTime lastActiveAt;
    private Integer skillScore;
    private String skillTier;
    private Integer matchCount;

    public static UserProfileVO from(User user) {
        if (user == null) {
            return null;
        }
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setGender(user.getGender());
        vo.setAvatar(user.getAvatar());
        vo.setBio(user.getBio());
        vo.setPuuid(user.getPuuid());
        vo.setSummonerName(user.getSummonerName());
        vo.setRegion(user.getRegion());
        vo.setPosition(user.getPosition());
        vo.setRankTier(user.getRankTier());
        vo.setStatus(user.getStatus());
        vo.setLastActiveAt(user.getLastActiveAt());
        vo.setSkillScore(user.getSkillScore());
        vo.setSkillTier(user.getSkillTier());
        vo.setMatchCount(user.getMatchCount());
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
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
