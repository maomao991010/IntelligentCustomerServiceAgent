package com.ticketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("chat_history")
public class ChatHistory implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("user_id")
    private Long userId;

    @TableField("session_id")
    private String sessionId;

    @TableField("question")
    private String question;

    @TableField("answer")
    private String answer;

    @TableField("intent")
    private String intent;

    @TableField("confidence")
    private BigDecimal confidence;

    @TableField("faq_id")
    private Long faqId;

    @TableField("is_transfer")
    private Integer isTransfer;

    @TableField("ai_generated")
    private Integer aiGenerated;

    @TableField("create_time")
    private LocalDateTime createTime;
}
