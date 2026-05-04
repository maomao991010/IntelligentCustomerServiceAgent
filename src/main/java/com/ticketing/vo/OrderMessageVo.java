package com.ticketing.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderMessageVo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String messageId;
    private String orderId;
    private String lockOrderId;
    private String userId;
    private Integer totalPrice;
    private String paymentMethod;
    private String paymentStatus;
    private String messageType;
    private Long timestamp;
    
    public static final String MSG_TYPE_CREATE = "CREATE";
    public static final String MSG_TYPE_PAY = "PAY";
    public static final String MSG_TYPE_CANCEL = "CANCEL";
}
