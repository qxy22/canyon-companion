package com.lol.server.controller;

import com.lol.server.dto.ApiResponse;
import com.lol.server.dto.UserProfileVO;
import com.lol.server.service.PresenceService;
import com.lol.server.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「人还在不在」的三个动作：进站、心跳、关页。
 *
 * 为什么不复用 PUT /user/status：
 * 那个接口是给用户**手动选**状态用的，语义是"我宣布我现在是什么状态"；
 * 这里要表达的是"我还开着页面"。两者混在一起的话，心跳会不停覆盖用户手动选的
 * 「游戏中」，而关页面又会把人从「游戏中」直接改成离线 —— 都不对。
 *
 * 所以这边只负责两件事：状态翻转走 ProfileService.updateStatus（它同时维护
 * 数据库和 Redis 索引），心跳只刷新活跃时间，不碰状态。
 */
@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    private final PresenceService presenceService;
    private final ProfileService profileService;

    public PresenceController(PresenceService presenceService, ProfileService profileService) {
        this.presenceService = presenceService;
        this.profileService = profileService;
    }

    /** 打开网页 / 登录成功：直接算在线。返回最新资料，前端拿去刷新身份卡。 */
    @PostMapping("/online")
    public ApiResponse<UserProfileVO> online(HttpServletRequest request) {
        try {
            return ApiResponse.ok(profileService.updateStatus(currentUserId(request), "ONLINE"));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 关闭网页 / 退出登录：直接算离线。
     *
     * 前端用的是 keepalive fetch，页面卸载后请求也会发完。
     * 万一没发出去（浏览器强杀、断网），服务端的心跳超时扫描会在 90 秒后兜住。
     */
    @PostMapping("/offline")
    public ApiResponse<UserProfileVO> offline(HttpServletRequest request) {
        try {
            return ApiResponse.ok(profileService.updateStatus(currentUserId(request), "OFFLINE"));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 心跳：每隔 25 秒一次，只把「最近活跃时间」刷新一下。
     *
     * online=false 表示这个人已经不在在线索引里了 —— 可能是被扫超时摘掉
     * （后台标签页睡过头、笔记本合盖），也可能是刚切成「游戏中」。
     * 前端只在「不在手动状态」时才补一次 online，所以这里不需要区分。
     */
    @PostMapping("/heartbeat")
    public ApiResponse<HeartbeatVO> heartbeat(HttpServletRequest request) {
        Boolean indexed = presenceService.touch(currentUserId(request));
        // null = Redis 不可用。这时"不知道"比"报不在"安全：报不在会让前端
        // 每 25 秒补一次 online，白白多一次写库。
        return ApiResponse.ok(new HeartbeatVO(indexed == null || indexed));
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    /** 心跳结果。字段名就是前端读的那个，不要再包一层。 */
    public record HeartbeatVO(boolean online) {
    }
}
