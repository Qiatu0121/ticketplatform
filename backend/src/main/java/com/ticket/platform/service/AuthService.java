package com.ticket.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.platform.common.BizException;
import com.ticket.platform.entity.User;
import com.ticket.platform.mapper.UserMapper;
import com.ticket.platform.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 登录鉴权：登录后签发随机 token 并存入 Redis（token -> userId），
 * 后续请求通过 Authorization: Bearer token 恢复用户身份。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String TOKEN_PREFIX = "auth:token:";
    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;

    /** 用户名或手机号登录，成功后返回 token 与用户信息。 */
    public LoginVO login(String account, String password) {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, account)
                        .or()
                        .eq(User::getPhone, account)
                        .last("limit 1"));

        User user = users.isEmpty() ? null : users.get(0);

        // 不区分「用户不存在」和「密码错误」，避免泄露账号是否存在
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException("用户名/手机号或密码错误");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                TOKEN_PREFIX + token,
                String.valueOf(user.getId()),
                TOKEN_TTL);

        // 响应中不返回密码哈希
        user.setPasswordHash(null);
        return LoginVO.of(user, token);
    }

    /** 根据 token 查询用户；token 不存在、过期或用户已删除时返回 null。 */
    public User getUserByToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String userId = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (userId == null) {
            return null;
        }
        return userMapper.selectById(Long.valueOf(userId));
    }

    /** 登出时删除 token，使其立即失效。 */
    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            redisTemplate.delete(TOKEN_PREFIX + token);
        }
    }

    /** 从请求头解析 Bearer token。 */
    public static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
