package com.ticket.platform.controller;

import com.ticket.platform.common.Result;
import com.ticket.platform.config.AuthInterceptor;
import com.ticket.platform.service.AuthService;
import com.ticket.platform.service.UserService;
import com.ticket.platform.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户注册、登录与登出。登录和注册接口匿名可访问。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    public Result<Long> register(
            @RequestParam String username,
            @RequestParam String phone,
            @RequestParam String nickname,
            @RequestParam String password) {
        return Result.ok(userService.register(username, phone, nickname, password));
    }

    /** account 支持用户名或手机号。 */
    @PostMapping("/login")
    public Result<LoginVO> login(
            @RequestParam String account,
            @RequestParam String password) {
        return Result.ok(authService.login(account, password));
    }

    /** 删除当前请求携带的 token。 */
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestAttribute(AuthInterceptor.LOGIN_USER_TOKEN_ATTR) String token) {
        authService.logout(token);
        return Result.ok(null);
    }
}
