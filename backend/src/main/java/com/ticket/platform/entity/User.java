package com.ticket.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** user 表：登录账号与角色信息。 */
@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String phone;

    private String nickname;

    /** USER 普通用户，ADMIN 管理员 */
    private String role;

    /** BCrypt 哈希，不保存明文密码 */
    private String passwordHash;

    private LocalDateTime createTime;
}
