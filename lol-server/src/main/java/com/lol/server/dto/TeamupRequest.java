package com.lol.server.dto;

import java.util.List;

/**
 * 发布开黑组队请求体。
 */
public class TeamupRequest {

    /** 模式 code：RANKED / NORMAL / ARAM */
    private String gameMode;

    /** 需要的位置 code 列表，如 ["TOP","JUNGLE"] */
    private List<String> positions;

    /** 说明 */
    private String description;

    /** 队伍人数上限，默认 5 */
    private Integer maxSize;

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public List<String> getPositions() {
        return positions;
    }

    public void setPositions(List<String> positions) {
        this.positions = positions;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(Integer maxSize) {
        this.maxSize = maxSize;
    }
}
