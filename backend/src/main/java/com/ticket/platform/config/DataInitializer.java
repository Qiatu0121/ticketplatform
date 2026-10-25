package com.ticket.platform.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.platform.entity.User;
import com.ticket.platform.mapper.UserMapper;
import com.ticket.platform.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 启动初始化：按环境变量创建管理员账号，并以数据库为准重建 Redis 库存与购买标记。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final OrderService orderService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${app.security.root-username:root}")
    private String rootUsername;

    @Value("${app.security.root-password:}")
    private String rootPassword;

    @Value("${app.security.root-phone:00000000000}")
    private String rootPhone;

    @Override
    public void run(String... args) {
        createRootUserIfAbsent();
        syncStockToRedis();
    }

    /**
     * 管理员账号只在显式配置 ROOT_ADMIN_PASSWORD 时创建。
     * 不提供默认密码，密码不会写入日志。
     */
    private void createRootUserIfAbsent() {
        if (rootUsername == null || rootUsername.isBlank()) {
            log.warn("未配置 ROOT_ADMIN_USERNAME，跳过默认管理员创建");
            return;
        }

        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, rootUsername));
        if (count != null && count > 0) {
            return;
        }

        if (rootPassword == null || rootPassword.isBlank()) {
            log.info("未配置 ROOT_ADMIN_PASSWORD，跳过默认管理员创建");
            return;
        }
        if (rootPassword.length() < 8) {
            log.warn("ROOT_ADMIN_PASSWORD 长度不足 8 位，跳过默认管理员创建");
            return;
        }

        User root = new User();
        root.setUsername(rootUsername);
        root.setPhone(rootPhone);
        root.setNickname("系统管理员");
        root.setRole("ADMIN");
        root.setPasswordHash(passwordEncoder.encode(rootPassword));
        root.setCreateTime(LocalDateTime.now());
        userMapper.insert(root);
        log.info("已创建默认管理员：{}（请登录后立即修改密码）", rootUsername);
    }

    /**
     * 以数据库为准重建 Redis 库存与购买标记。
     * 该逻辑只在启动、尚未接收请求时执行，适用于单实例部署。
     */
    private void syncStockToRedis() {
        try {
            int corrected = orderService.reconcileStockFromDb();
            log.info("Redis 库存与购买标记已按数据库同步，修正 {} 个票种库存", corrected);
        } catch (Exception e) {
            log.warn("Redis 库存同步失败，请检查 Redis 连接", e);
        }
    }
}
