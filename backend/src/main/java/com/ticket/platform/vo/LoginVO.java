package com.ticket.platform.vo;

import com.ticket.platform.entity.User;
import lombok.Data;

/**
 * 登录响应：token 与已脱敏的用户信息。
 */
@Data
public class LoginVO {

    private User user;

    private String token;

    public static LoginVO of(User user, String token) {
        LoginVO vo = new LoginVO();
        vo.setUser(user);
        vo.setToken(token);
        return vo;
    }
}
