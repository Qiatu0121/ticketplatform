package com.ticket.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动类。开启定时任务并扫描 MyBatis-Plus Mapper。
 */
@EnableScheduling
@MapperScan("com.ticket.platform.mapper")
@SpringBootApplication
public class TicketLearnApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketLearnApplication.class, args);
    }
}
