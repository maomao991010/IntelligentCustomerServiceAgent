package com.ticketing.controller;

import com.ticketing.annotation.RequirePermission;
import com.ticketing.config.SlowSqlInterceptor;
import com.ticketing.vo.ResponseVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/monitor")
@RequirePermission("system:monitor")
public class MonitorController {

    private final SlowSqlInterceptor slowSqlInterceptor;

    public MonitorController(SlowSqlInterceptor slowSqlInterceptor) {
        this.slowSqlInterceptor = slowSqlInterceptor;
    }

    @GetMapping("/slow-sql")
    public ResponseVo getSlowSqlStats() {
        ConcurrentHashMap<String, AtomicLong> stats = slowSqlInterceptor.getSlowSqlStats();
        List<Map<String, Object>> result = new ArrayList<>();
        stats.forEach((sqlId, count) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("sqlId", sqlId);
            item.put("slowCount", count.get());
            result.add(item);
        });
        result.sort((a, b) -> Long.compare((Long) b.get("slowCount"), (Long) a.get("slowCount")));
        return ResponseVo.success(result);
    }
}
