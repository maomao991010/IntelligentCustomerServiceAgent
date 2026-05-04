package com.ticketing.service;

import com.ticketing.entity.User;
import com.ticketing.vo.LoginVo;
import com.ticketing.vo.RegisterVo;
import com.ticketing.vo.ResponseVo;

import java.util.List;

public interface UserService {
    ResponseVo login(LoginVo loginVo);
    ResponseVo register(RegisterVo registerVo);
    ResponseVo getVerificationCode();
    ResponseVo sendEmailVerificationCode(String email);
    boolean verifyToken(String token);
    ResponseVo logout(String token);
    User getUserByPhone(String phone);
    User getUserByEmail(String email);
    ResponseVo getAllUsers();
    List<User> getAllUsersList();
    User getUserById(Long userId);
    ResponseVo updateUserInfo(Long userId, String nickname, String email);
    ResponseVo changePassword(Long userId, String oldPassword, String newPassword);
    ResponseVo updateAvatar(Long userId, String avatarUrl);
    ResponseVo bindPhone(Long userId, String phone, String verificationCode);
    ResponseVo bindEmail(Long userId, String email, String verificationCode);
    ResponseVo unbindPhone(Long userId);
    ResponseVo unbindEmail(Long userId);
}
