package com.ticketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("`session`")
public class Session implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private String activityName;

    private String date;

    private String time;

    private String venue;

    private Integer totalSeats;

    private Integer remainingSeats;

    private Integer minPrice;

    private Integer maxPrice;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String imagePath;

    private String artist;

}
