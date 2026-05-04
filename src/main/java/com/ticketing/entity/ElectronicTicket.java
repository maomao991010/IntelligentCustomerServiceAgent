package com.ticketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("electronic_ticket")
public class ElectronicTicket implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ticketCode;

    private String orderId;

    private Long userId;

    private Long sessionId;

    private String activityName;

    private String sessionDate;

    private String sessionTime;

    private String venue;

    private String seatInfo;

    private String seatType;

    private Integer price;

    private String status;

    private String qrCodePath;

    private String pdfPath;

    private LocalDateTime generatedTime;

    private LocalDateTime usedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
