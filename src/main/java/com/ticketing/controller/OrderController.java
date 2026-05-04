package com.ticketing.controller;

import com.ticketing.service.OrderService;
import com.ticketing.vo.CreateOrderVo;
import com.ticketing.vo.ResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@Validated
@Tag(name = "订单管理", description = "订单创建、查询、支付、取消等接口")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/create")
    @Operation(summary = "创建订单", description = "根据锁定信息创建新订单")
    public ResponseVo createOrder(@Valid @RequestBody CreateOrderVo createOrderVo) {
        return orderService.createOrder(createOrderVo);
    }

    @GetMapping("/detail/{orderId}")
    @Operation(summary = "获取订单详情", description = "根据订单ID获取订单详细信息")
    public ResponseVo getOrderDetail(@PathVariable String orderId) {
        return orderService.getOrderDetail(orderId);
    }

    @GetMapping("/list")
    @Operation(summary = "获取订单列表", description = "根据用户ID获取该用户的所有订单")
    public ResponseVo getOrderList(@RequestParam @NotNull(message = "用户ID不能为空") Long userId) {
        return orderService.getOrderList(userId);
    }

    @GetMapping("/page")
    @Operation(summary = "分页获取订单列表", description = "根据用户ID分页获取订单列表，支持关键词搜索")
    public ResponseVo getOrderPage(
            @RequestParam @NotNull(message = "用户ID不能为空") Long userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return orderService.getOrderPage(userId, pageNum, pageSize, keyword);
    }

    @PostMapping("/pay")
    @Operation(summary = "支付订单", description = "根据订单ID和支付方式支付订单")
    public ResponseVo payOrder(
            @RequestParam @NotBlank(message = "订单ID不能为空") String orderId,
            @RequestParam @NotBlank(message = "支付方式不能为空") String paymentMethod) {
        return orderService.payOrder(orderId, paymentMethod);
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消订单", description = "根据订单ID取消订单")
    public ResponseVo cancelOrder(@RequestParam @NotBlank(message = "订单ID不能为空") String orderId,
                                   @RequestParam(required = false) String cancelReason) {
        if (cancelReason != null && !cancelReason.isEmpty()) {
            return orderService.cancelOrder(orderId, cancelReason);
        }
        return orderService.cancelOrder(orderId);
    }

    @PostMapping("/payment-callback")
    @Operation(summary = "支付回调", description = "处理支付平台的支付回调通知")
    public ResponseVo paymentCallback(
            @RequestParam @NotBlank(message = "订单ID不能为空") String orderId,
            @RequestParam @NotBlank(message = "支付状态不能为空") String paymentStatus,
            @RequestParam @NotBlank(message = "支付金额不能为空") String paymentAmount,
            @RequestParam @NotBlank(message = "支付时间不能为空") String paymentTime) {
        orderService.processPaymentCallback(orderId, paymentStatus, paymentAmount, paymentTime);
        return ResponseVo.success(null);
    }
}
