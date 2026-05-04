package com.ticketing.controller;

import com.ticketing.entity.Session;
import com.ticketing.service.SessionService;
import com.ticketing.vo.ResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sessions")
@Validated
@Tag(name = "场次管理", description = "场次列表查询、详情查询、增删改等接口")
public class SessionController {

    @Autowired
    private SessionService sessionService;

    @GetMapping
    @Operation(summary = "获取场次列表", description = "查询所有场次，可按活动ID筛选")
    public ResponseVo getSessionList(@RequestParam(required = false) Long activityId) {
        return sessionService.getSessionList(activityId);
    }

    @GetMapping("/page")
    @Operation(summary = "分页获取场次列表", description = "分页查询场次，支持关键词搜索")
    public ResponseVo getSessionPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return sessionService.getSessionPage(pageNum, pageSize, keyword);
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "获取场次详情", description = "根据场次ID获取场次详细信息")
    public ResponseVo getSessionDetail(@PathVariable @NotNull(message = "场次ID不能为空") Long sessionId) {
        return sessionService.getSessionDetail(sessionId);
    }

    @PostMapping
    @Operation(summary = "添加场次", description = "新增一个场次")
    public ResponseVo addSession(@Valid @RequestBody Session session) {
        return sessionService.addSession(session);
    }

    @PutMapping("/{sessionId}")
    @Operation(summary = "更新场次", description = "根据场次ID更新场次信息")
    public ResponseVo updateSession(@PathVariable @NotNull(message = "场次ID不能为空") Long sessionId, @Valid @RequestBody Session session) {
        session.setId(sessionId);
        return sessionService.updateSession(session);
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "删除场次", description = "根据场次ID删除场次")
    public ResponseVo deleteSession(@PathVariable @NotNull(message = "场次ID不能为空") Long sessionId) {
        return sessionService.deleteSession(sessionId);
    }
}
