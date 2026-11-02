package com.lol.server.dto;

import java.util.List;

/**
 * 匹配推荐结果：一个候选用户 + 匹配分 + 推荐理由。
 */
public class RecommendVO {

    private UserProfileVO user;
    private Integer matchScore;
    private List<String> reasons;

    public RecommendVO() {
    }

    public RecommendVO(UserProfileVO user, Integer matchScore, List<String> reasons) {
        this.user = user;
        this.matchScore = matchScore;
        this.reasons = reasons;
    }

    public UserProfileVO getUser() {
        return user;
    }

    public void setUser(UserProfileVO user) {
        this.user = user;
    }

    public Integer getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Integer matchScore) {
        this.matchScore = matchScore;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }
}
