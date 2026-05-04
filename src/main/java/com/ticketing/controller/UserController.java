package com.ticketing.controller;

import com.ticketing.annotation.OperationLog;
import com.ticketing.annotation.RequirePermission;
import com.ticketing.entity.User;
import com.ticketing.service.AuthService;
import com.ticketing.service.UserService;
import com.ticketing.utils.JwtUtil;
import com.ticketing.utils.SM2Util;
import com.ticketing.vo.LoginVo;
import com.ticketing.vo.RegisterVo;
import com.ticketing.vo.ResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("")
@Validated
@Tag(name = "用户认证", description = "用户登录、注册、信息等接口")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/auth/login")
    @OperationLog(value = "用户登录", module = "认证", type = OperationLog.OperType.LOGIN)
    @Operation(summary = "用户登录", description = "通过手机号/邮箱+密码登录")
    public ResponseVo login(@Valid @RequestBody LoginVo loginVo) {
        return userService.login(loginVo);
    }

    @PostMapping("/auth/register")
    @OperationLog(value = "用户注册", module = "认证", type = OperationLog.OperType.CREATE)
    @Operation(summary = "用户注册", description = "注册新用户账号")
    public ResponseVo register(@Valid @RequestBody RegisterVo registerVo) {
        return userService.register(registerVo);
    }

    @GetMapping("/auth/verification-code")
    @Operation(summary = "获取图形验证码", description = "获取登录/注册所需的图形验证码")
    public ResponseVo getVerificationCode() {
        return userService.getVerificationCode();
    }

    @PostMapping("/auth/verify-token")
    @Operation(summary = "验证Token", description = "验证JWT Token是否有效")
    public ResponseVo verifyToken(@RequestParam String token) {
        boolean valid = userService.verifyToken(token);
        if (valid) {
            return ResponseVo.success(null);
        } else {
            return ResponseVo.error(401, "令牌无效");
        }
    }

    @PostMapping("/auth/logout")
    @OperationLog(value = "用户登出", module = "认证", type = OperationLog.OperType.LOGOUT)
    @Operation(summary = "用户登出", description = "退出登录，使Token失效")
    public ResponseVo logout(@RequestParam String token) {
        return userService.logout(token);
    }

    @PostMapping("/auth/send-email-code")
    @Operation(summary = "发送邮箱验证码", description = "向指定邮箱发送验证码")
    public ResponseVo sendEmailVerificationCode(
            @RequestParam @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email) {
        return userService.sendEmailVerificationCode(email);
    }

    @GetMapping("/auth/sm2-public-key")
    @Operation(summary = "获取SM2公钥", description = "获取SM2加密公钥用于密码加密传输")
    public ResponseVo getSM2PublicKey() {
        String publicKey = SM2Util.getPublicKey();
        return ResponseVo.success(publicKey);
    }

    @GetMapping("/users")
    @RequirePermission("role:assign")
    @Operation(summary = "获取所有用户列表", description = "需要角色分配权限")
    public ResponseVo getAllUsers() {
        List<User> users = userService.getAllUsersList();
        List<Map<String, Object>> result = users.stream().map(user -> {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", user.getId());
            userMap.put("phone", user.getPhone());
            userMap.put("email", user.getEmail());
            userMap.put("nickname", user.getNickname());
            userMap.put("status", user.getStatus());
            userMap.put("createTime", user.getCreateTime());
            userMap.put("lastLoginTime", user.getLastLoginTime());
            return userMap;
        }).collect(Collectors.toList());
        return ResponseVo.success(result);
    }

    @GetMapping("/user/info")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的信息、权限和角色")
    public ResponseVo getCurrentUserInfo(@RequestHeader("Authorization") String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        Long userId = jwtUtil.getUserIdFromToken(token);
        User user = userService.getUserById(userId);
        
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("phone", user.getPhone());
        userInfo.put("email", user.getEmail());
        
        List<String> permissions = authService.getUserPermissions(userId);
        List<String> roles = authService.getUserRoleCodes(userId);
        
        Map<String, Object> data = new HashMap<>();
        data.put("user", userInfo);
        data.put("permissions", permissions);
        data.put("roles", roles);
        
        return ResponseVo.success(data);
    }
}
