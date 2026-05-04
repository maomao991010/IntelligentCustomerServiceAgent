package com.ticketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("`order`")
public class Order implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderId;

    private String lockOrderId;

    private Long userId;

    private String userPhone;

    private Long activityId;

    private String activityName;

    private Long sessionId;

    private String sessionDate;

    private String sessionTime;

    private String venue;

    private String seatInfo;

    private Integer totalPrice;

    private String paymentMethod;

    private String paymentStatus;

    private String orderStatus;

    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime expireTime;

    private String cancelReason;

    private LocalDateTime cancelTime;

    private String refundStatus;

    private LocalDateTime refundTime;

    private Integer refundAmount;

    private String ticketCode;

    private LocalDateTime ticketGeneratedTime;
}
