package com.ticket.platform.service;

import com.ticket.platform.entity.User;
import com.ticket.platform.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户注册。注册用户固定为 USER 角色，密码使用 BCrypt 加密存储。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 创建普通用户并返回主键；用户名或手机号重复时由数据库唯一索引拦截。 */
    public Long register(String username, String phone, String nickname, String password) {
        User user = new User();
        user.setUsername(username);
        user.setPhone(phone);
        user.setNickname(nickname);
        user.setRole("USER");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        return user.getId();
    }
}
