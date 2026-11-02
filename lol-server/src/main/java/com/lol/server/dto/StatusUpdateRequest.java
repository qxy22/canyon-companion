package com.lol.server.dto;

/**
 * 切换在线状态请求体：status 取值 ONLINE / OFFLINE / BUSY。
 */
public class StatusUpdateRequest {

    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
