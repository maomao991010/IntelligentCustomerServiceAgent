package com.ticketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;

    private String email;

    private String password;

    private String nickname;

    private String avatarUrl;

    private Integer status;

    private Integer loginFailCount;

    private LocalDateTime lastLoginTime;

    private LocalDateTime lockTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
