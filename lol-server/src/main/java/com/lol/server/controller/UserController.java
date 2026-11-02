package com.lol.server.controller;

import com.lol.server.dto.ApiResponse;
import com.lol.server.dto.ProfileUpdateRequest;
import com.lol.server.dto.StatusUpdateRequest;
import com.lol.server.dto.UserProfileVO;
import com.lol.server.dto.UserVO;
import com.lol.server.entity.User;
import com.lol.server.service.ProfileService;
import com.lol.server.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final ProfileService profileService;

    public UserController(UserService userService, ProfileService profileService) {
        this.userService = userService;
        this.profileService = profileService;
    }

    /** 当前登录用户信息（受 JWT 拦截器保护）。 */
    @GetMapping("/info")
    public ApiResponse<UserVO> info(HttpServletRequest request) {
        Long userId = currentUserId(request);
        User user = userService.getById(userId);
        return ApiResponse.ok(new UserVO(user.getId(), user.getUsername(), user.getNickname()));
    }

    /** 当前用户交友资料。 */
    @GetMapping("/profile")
    public ApiResponse<UserProfileVO> profile(HttpServletRequest request) {
        return ApiResponse.ok(profileService.getProfile(currentUserId(request)));
    }

    /**
     * 查看任意用户的公开资料。
     * 原来的 /api/user/profile 只能查自己，而「点击组员看他的资料」需要一个
     * 能按 id 取资料的接口，否则资料页无法刷新、也无法直接分享链接。
     */
    @GetMapping("/{id}/profile")
    public ApiResponse<UserProfileVO> publicProfile(@PathVariable Long id) {
        try {
            return ApiResponse.ok(profileService.getProfile(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 更新资料（传了哪个字段就更新哪个）。 */
    @PutMapping("/profile")
    public ApiResponse<UserProfileVO> updateProfile(HttpServletRequest request,
                                                    @RequestBody ProfileUpdateRequest req) {
        try {
            return ApiResponse.ok(profileService.updateProfile(currentUserId(request), req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 更换头像。图片走这个接口上传，而不是把 URL 塞进「编辑资料」：
     * 手填 URL 会让头像变成任何外部服务器上的内容，还能当成探针使。
     * 换下来的旧图立刻删掉，删不掉的留给孤儿文件扫描。
     */
    @PostMapping("/avatar")
    public ApiResponse<UserProfileVO> uploadAvatar(HttpServletRequest request,
                                                   @RequestParam("file") MultipartFile file) {
        try {
            return ApiResponse.ok(profileService.updateAvatar(currentUserId(request), file));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 移除头像，回到昵称首字母的默认显示。 */
    @DeleteMapping("/avatar")
    public ApiResponse<UserProfileVO> removeAvatar(HttpServletRequest request) {
        try {
            return ApiResponse.ok(profileService.removeAvatar(currentUserId(request)));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 切换在线状态：ONLINE/OFFLINE/BUSY。 */
    @PutMapping("/status")
    public ApiResponse<UserProfileVO> updateStatus(HttpServletRequest request,
                                                   @RequestBody StatusUpdateRequest req) {
        try {
            return ApiResponse.ok(profileService.updateStatus(currentUserId(request), req.getStatus()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
