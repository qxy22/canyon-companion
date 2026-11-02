package com.lol.server.controller;

import com.lol.server.dto.ApiResponse;
import com.lol.server.dto.LoginRequest;
import com.lol.server.dto.LoginResponse;
import com.lol.server.dto.RegisterRequest;
import com.lol.server.dto.UserVO;
import com.lol.server.entity.User;
import com.lol.server.service.UserService;
import com.lol.common.util.JwtUtil;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    /** 注册 */
    @PostMapping("/register")
    public ApiResponse<?> register(@RequestBody RegisterRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank()
                || req.getNickname() == null || req.getNickname().isBlank()
                || req.getPassword() == null || req.getPassword().isBlank()) {
            return ApiResponse.error(400, "用户名、昵称和密码都不能为空");
        }
        try {
            User user = userService.register(req.getUsername(), req.getPassword(), req.getNickname());
            return ApiResponse.ok(new UserVO(user.getId(), user.getUsername(), user.getNickname()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 登录：成功后签发 JWT */
    @PostMapping("/login")
    public ApiResponse<?> login(@RequestBody LoginRequest req) {
        try {
            User user = userService.login(req.getUsername(), req.getPassword());
            String token = jwtUtil.generate(user.getId(), user.getUsername());
            UserVO vo = new UserVO(user.getId(), user.getUsername(), user.getNickname());
            return ApiResponse.ok(new LoginResponse(token, vo));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
