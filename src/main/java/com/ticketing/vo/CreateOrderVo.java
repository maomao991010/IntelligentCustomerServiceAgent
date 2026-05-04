package com.ticketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "创建订单请求")
public class CreateOrderVo {

    @Schema(description = "锁定订单ID")
    @NotBlank(message = "锁定订单ID不能为空")
    private String lockOrderId;

    @Schema(description = "支付方式")
    @NotBlank(message = "支付方式不能为空")
    private String paymentMethod;

    @Schema(description = "总价（分）")
    @NotNull(message = "总价不能为空")
    private Integer totalPrice;
}
