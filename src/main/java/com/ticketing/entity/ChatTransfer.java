package com.ticketing.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_transfer")
public class ChatTransfer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String sessionId;

    private Long userId;

    private String userName;

    private String userPhone;

    private String question;

    private String status;

    private Long agentId;

    private String agentName;

    private LocalDateTime transferTime;

    private LocalDateTime handleTime;

    private LocalDateTime closeTime;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
