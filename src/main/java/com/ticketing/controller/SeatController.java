package com.ticketing.controller;

import com.ticketing.service.SeatService;
import com.ticketing.vo.ResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/seats")
@Validated
@Tag(name = "座位管理", description = "座位图查询、座位生成等接口")
public class SeatController {

    @Autowired
    private SeatService seatService;

    @GetMapping("/map/{sessionId}")
    @Operation(summary = "获取座位图", description = "根据场次ID获取该场次的座位分布和状态信息")
    public ResponseVo getSeatMap(@PathVariable @NotNull(message = "场次ID不能为空") Long sessionId) {
        return seatService.getSeatMap(sessionId);
    }

    @PostMapping("/generate")
    @Operation(summary = "生成座位", description = "根据场次ID和总座位数生成座位信息")
    public ResponseVo generateSeats(@RequestBody Map<String, Object> request) {
        Long sessionId = Long.parseLong(request.get("sessionId").toString());
        int totalSeats = Integer.parseInt(request.get("totalSeats").toString());
        return seatService.generateSeats(sessionId, totalSeats);
    }
}
