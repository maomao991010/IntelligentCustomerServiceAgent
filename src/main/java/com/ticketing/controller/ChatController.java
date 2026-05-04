package com.ticketing.controller;

import com.ticketing.service.ChatService;
import com.ticketing.annotation.RequirePermission;
import com.ticketing.entity.Faq;
import com.ticketing.vo.ChatRequest;
import com.ticketing.vo.ChatResponse;
import com.ticketing.vo.ResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")
@Validated
@Tag(name = "AI客服", description = "AI智能客服聊天与FAQ管理接口")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping("/send")
    @Operation(summary = "发送聊天消息", description = "向AI客服发送消息并获取回复")
    public ResponseVo sendMessage(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatService.chat(request);
        return ResponseVo.success(response);
    }

    @GetMapping("/history/{sessionId}")
    @Operation(summary = "获取聊天历史", description = "根据会话ID获取聊天历史记录")
    public ResponseVo getHistory(@PathVariable String sessionId) {
        return ResponseVo.success(chatService.getHistory(sessionId));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户聊天记录", description = "根据用户ID获取所有聊天记录")
    public ResponseVo getUserHistory(@PathVariable Long userId) {
        return ResponseVo.success(chatService.getUserHistory(userId));
    }

    @GetMapping("/faq/page")
    @RequirePermission("faq:view")
    @Operation(summary = "分页获取FAQ列表", description = "管理端分页查询FAQ，需要faq:view权限")
    public ResponseVo getFaqPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String category) {
        return ResponseVo.success(chatService.getFaqPage(pageNum, pageSize, category));
    }

    @GetMapping("/faq/list")
    @Operation(summary = "获取所有活跃FAQ", description = "前台用户获取所有启用的FAQ列表")
    public ResponseVo getAllFaqs() {
        return ResponseVo.success(chatService.getAllActiveFaqs());
    }

    @GetMapping("/faq/{id}")
    @RequirePermission("faq:view")
    @Operation(summary = "获取FAQ详情", description = "根据ID获取FAQ详情，需要faq:view权限")
    public ResponseVo getFaq(@PathVariable Long id) {
        return ResponseVo.success(chatService.getFaqById(id));
    }

    @PostMapping("/faq")
    @RequirePermission("faq:add")
    @Operation(summary = "创建FAQ", description = "新增FAQ，需要faq:add权限")
    public ResponseVo createFaq(@Valid @RequestBody Faq faq) {
        Faq created = chatService.createFaq(faq);
        return ResponseVo.success(created);
    }

    @PutMapping("/faq/{id}")
    @RequirePermission("faq:edit")
    @Operation(summary = "更新FAQ", description = "修改FAQ，需要faq:edit权限")
    public ResponseVo updateFaq(@PathVariable Long id, @Valid @RequestBody Faq faq) {
        faq.setId(id);
        Faq updated = chatService.updateFaq(faq);
        if (updated != null) {
            return ResponseVo.success(updated);
        } else {
            return ResponseVo.error("FAQ不存在");
        }
    }

    @DeleteMapping("/faq/{id}")
    @RequirePermission("faq:delete")
    @Operation(summary = "删除FAQ", description = "删除FAQ，需要faq:delete权限")
    public ResponseVo deleteFaq(@PathVariable Long id) {
        boolean success = chatService.deleteFaq(id);
        if (success) {
            return ResponseVo.success("删除成功");
        } else {
            return ResponseVo.error("FAQ不存在");
        }
    }
}
