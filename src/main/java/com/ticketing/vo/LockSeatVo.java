package com.ticketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "锁定座位请求")
public class LockSeatVo {

    @Schema(description = "场次ID")
    @NotNull(message = "场次ID不能为空")
    private Long sessionId;

    @Schema(description = "座位ID列表")
    @NotEmpty(message = "座位列表不能为空")
    private List<Long> seatIds;

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "验证码")
    private String verificationCode;
}
