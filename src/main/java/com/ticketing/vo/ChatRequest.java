package com.ticketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "聊天请求")
public class ChatRequest {

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "用户ID")
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "用户问题")
    @NotBlank(message = "问题内容不能为空")
    private String question;
}
