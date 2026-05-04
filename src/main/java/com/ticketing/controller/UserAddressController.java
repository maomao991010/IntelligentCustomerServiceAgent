package com.ticketing.controller;

import com.ticketing.annotation.OperationLog;
import com.ticketing.annotation.OperationLog.OperType;
import com.ticketing.entity.UserAddress;
import com.ticketing.service.UserAddressService;
import com.ticketing.utils.JwtUtil;
import com.ticketing.vo.ResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user-center/addresses")
@Tag(name = "收货地址", description = "收货地址管理接口")
public class UserAddressController {

    @Autowired
    private UserAddressService userAddressService;

    @Autowired
    private JwtUtil jwtUtil;

    private Long getUserIdFromToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return jwtUtil.getUserIdFromToken(token);
    }

    @GetMapping
    @Operation(summary = "获取地址列表", description = "获取当前用户的所有收货地址")
    public ResponseVo getAddressList(@RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return userAddressService.getAddressList(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取地址详情", description = "获取指定地址的详情")
    public ResponseVo getAddressById(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        Long userId = getUserIdFromToken(token);
        return userAddressService.getAddressById(userId, id);
    }

    @PostMapping
    @OperationLog(value = "新增收货地址", module = "用户中心", type = OperType.CREATE)
    @Operation(summary = "新增收货地址", description = "添加新的收货地址")
    public ResponseVo addAddress(
            @RequestHeader("Authorization") String token,
            @RequestBody UserAddress address) {
        Long userId = getUserIdFromToken(token);
        return userAddressService.addAddress(userId, address);
    }

    @PutMapping("/{id}")
    @OperationLog(value = "修改收货地址", module = "用户中心", type = OperType.UPDATE)
    @Operation(summary = "修改收货地址", description = "修改指定的收货地址")
    public ResponseVo updateAddress(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @RequestBody UserAddress address) {
        Long userId = getUserIdFromToken(token);
        address.setId(id);
        return userAddressService.updateAddress(userId, address);
    }

    @DeleteMapping("/{id}")
    @OperationLog(value = "删除收货地址", module = "用户中心", type = OperType.DELETE)
    @Operation(summary = "删除收货地址", description = "删除指定的收货地址")
    public ResponseVo deleteAddress(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        Long userId = getUserIdFromToken(token);
        return userAddressService.deleteAddress(userId, id);
    }

    @PutMapping("/{id}/default")
    @OperationLog(value = "设置默认地址", module = "用户中心", type = OperType.UPDATE)
    @Operation(summary = "设置默认地址", description = "将指定地址设为默认地址")
    public ResponseVo setDefaultAddress(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        Long userId = getUserIdFromToken(token);
        return userAddressService.setDefaultAddress(userId, id);
    }
}
