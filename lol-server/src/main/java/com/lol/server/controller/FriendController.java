package com.lol.server.controller;

import com.lol.server.dto.ApiResponse;
import com.lol.server.dto.UserProfileVO;
import com.lol.server.service.FriendService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/friend")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    /** 我的好友列表。 */
    @GetMapping("/list")
    public ApiResponse<List<UserProfileVO>> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ApiResponse.ok(friendService.list(userId));
    }

    /** 好友数，顶栏徽标用。 */
    @GetMapping("/count")
    public ApiResponse<Long> count(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ApiResponse.ok(friendService.count(userId));
    }

    /**
     * 我和某个人是不是好友。
     * 玩家资料页要靠它决定显示「加好友」还是「已是好友」，
     * 为此拉一遍完整好友列表太浪费。
     */
    @GetMapping("/{userId}/exists")
    public ApiResponse<Boolean> exists(HttpServletRequest request, @PathVariable Long userId) {
        Long me = (Long) request.getAttribute("userId");
        return ApiResponse.ok(friendService.isFriend(me, userId));
    }

    /** 加好友。成功后对方会立刻收到提醒（带提示音）。 */
    @PostMapping("/{userId}")
    public ApiResponse<UserProfileVO> add(HttpServletRequest request, @PathVariable Long userId) {
        Long me = (Long) request.getAttribute("userId");
        try {
            return ApiResponse.ok(friendService.add(me, userId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 删好友。 */
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> remove(HttpServletRequest request, @PathVariable Long userId) {
        Long me = (Long) request.getAttribute("userId");
        try {
            friendService.remove(me, userId);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 邀请好友加入我当前的队伍。对方会收到一条可接受的邀请。 */
    @PostMapping("/{userId}/invite")
    public ApiResponse<Void> invite(HttpServletRequest request, @PathVariable Long userId) {
        Long me = (Long) request.getAttribute("userId");
        try {
            friendService.invite(me, userId);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
