package com.ticket.platform.config;

import com.ticket.platform.common.BizException;
import com.ticket.platform.entity.User;
import com.ticket.platform.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录鉴权：从 Authorization: Bearer token 还原用户，写入 request attribute；
 * /api/admin/** 额外要求 ADMIN 角色。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String LOGIN_USER_ATTR = "loginUser";
    public static final String LOGIN_USER_TOKEN_ATTR = "loginToken";

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        // 跨域预检请求不携带 token
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = AuthService.resolveToken(request);
        User user = authService.getUserByToken(token);
        if (user == null) {
            throw new BizException("未登录或登录已过期");
        }

        if (request.getRequestURI().startsWith("/api/admin/") && !"ADMIN".equals(user.getRole())) {
            throw new BizException("无权限，仅管理员可操作");
        }

        request.setAttribute(LOGIN_USER_ATTR, user);
        request.setAttribute(LOGIN_USER_TOKEN_ATTR, token);
        return true;
    }
}
