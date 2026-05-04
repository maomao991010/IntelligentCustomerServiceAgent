package com.ticketing.controller;

import com.ticketing.annotation.OperationLog;
import com.ticketing.annotation.OperationLog.OperType;
import com.ticketing.service.UserService;
import com.ticketing.utils.JwtUtil;
import com.ticketing.vo.ResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user-center")
@Validated
@Tag(name = "用户中心", description = "个人信息、密码、头像、绑定等接口")
public class UserCenterController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    private Long getUserIdFromToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return jwtUtil.getUserIdFromToken(token);
    }

    @PutMapping("/profile")
    @OperationLog(value = "修改个人信息", module = "用户中心", type = OperType.UPDATE)
    @Operation(summary = "修改个人信息", description = "修改昵称和邮箱")
    public ResponseVo updateProfile(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> body) {
        Long userId = getUserIdFromToken(token);
        String nickname = body.get("nickname");
        String email = body.get("email");
        return userService.updateUserInfo(userId, nickname, email);
    }

    @PutMapping("/password")
    @OperationLog(value = "修改密码", module = "用户中心", type = OperType.UPDATE)
    @Operation(summary = "修改密码", description = "修改登录密码")
    public ResponseVo changePassword(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> body) {
        Long userId = getUserIdFromToken(token);
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || newPassword == null) {
            return ResponseVo.error(400, "原密码和新密码不能为空");
        }
        return userService.changePassword(userId, oldPassword, newPassword);
    }

    @PutMapping("/avatar")
    @OperationLog(value = "更新头像", module = "用户中心", type = OperType.UPDATE)
    @Operation(summary = "更新头像", description = "更新用户头像URL")
    public ResponseVo updateAvatar(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> body) {
        Long userId = getUserIdFromToken(token);
        String avatarUrl = body.get("avatarUrl");
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            return ResponseVo.error(400, "头像URL不能为空");
        }
        return userService.updateAvatar(userId, avatarUrl);
    }

    @PostMapping("/bind-phone")
    @OperationLog(value = "绑定手机号", module = "用户中心", type = OperType.UPDATE)
    @Operation(summary = "绑定手机号", description = "绑定手机号，需验证码")
    public ResponseVo bindPhone(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> body) {
        Long userId = getUserIdFromToken(token);
        String phone = body.get("phone");
        String verificationCode = body.get("verificationCode");
        return userService.bindPhone(userId, phone, verificationCode);
    }

    @PostMapping("/bind-email")
    @OperationLog(value = "绑定邮箱", module = "用户中心", type = OperType.UPDATE)
    @Operation(summary = "绑定邮箱", description = "绑定邮箱，需验证码")
    public ResponseVo bindEmail(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> body) {
        Long userId = getUserIdFromToken(token);
        String email = body.get("email");
        String verificationCode = body.get("verificationCode");
        return userService.bindEmail(userId, email, verificationCode);
    }

    @PostMapping("/unbind-phone")
    @OperationLog(value = "解绑手机号", module = "用户中心", type = OperType.UPDATE)
    @Operation(summary = "解绑手机号", description = "解绑手机号，需确保已绑定邮箱")
    public ResponseVo unbindPhone(@RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return userService.unbindPhone(userId);
    }

    @PostMapping("/unbind-email")
    @OperationLog(value = "解绑邮箱", module = "用户中心", type = OperType.UPDATE)
    @Operation(summary = "解绑邮箱", description = "解绑邮箱，需确保已绑定手机号")
    public ResponseVo unbindEmail(@RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return userService.unbindEmail(userId);
    }
}
