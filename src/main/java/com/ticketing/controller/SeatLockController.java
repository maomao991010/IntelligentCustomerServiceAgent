package com.ticketing.controller;

import com.ticketing.service.SeatLockService;
import com.ticketing.vo.LockSeatVo;
import com.ticketing.vo.ResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/seats")
@Validated
@Tag(name = "座位锁定", description = "座位锁定与释放接口")
public class SeatLockController {

    @Autowired
    private SeatLockService seatLockService;

    @PostMapping("/lock")
    @Operation(summary = "锁定座位", description = "锁定指定的座位")
    public ResponseVo lockSeats(@Valid @RequestBody LockSeatVo lockSeatVo) {
        return seatLockService.lockSeats(lockSeatVo);
    }

    @PostMapping("/release")
    @Operation(summary = "释放座位", description = "释放已锁定的座位")
    public ResponseVo releaseSeats(@RequestParam @NotBlank(message = "锁定订单ID不能为空") String lockOrderId) {
        return seatLockService.releaseSeats(lockOrderId);
    }
}
