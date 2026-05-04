package com.ticketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("seat")
public class Seat implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    @TableField("`row_number`")
    private Integer rowNumber;

    @TableField("`seat_number`")
    private Integer seatNumber;

    @TableField("seat_type")
    private String seatType;

    @TableField("price")
    private Integer price;

    @TableField("status")
    private String status;

    @TableField("lock_user_id")
    private String lockUserId;

    @TableField("lock_time")
    private LocalDateTime lockTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
