package com.ticketing.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ticketing.annotation.RequirePermission;
import com.ticketing.entity.OperationLogEntity;
import com.ticketing.service.OperationLogService;
import com.ticketing.vo.ResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/operation-logs")
@Tag(name = "操作日志", description = "操作日志审计接口")
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping("/page")
    @RequirePermission("log:view")
    @Operation(summary = "分页查询操作日志")
    public ResponseVo getLogPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        IPage<OperationLogEntity> page = operationLogService.getLogPage(pageNum, pageSize, userId, module, type, startTime, endTime);
        Map<String, Object> result = new HashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("pageNum", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("pages", page.getPages());
        return ResponseVo.success(result);
    }
}
