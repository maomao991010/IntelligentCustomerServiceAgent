package com.ticketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ticketing.dao.SysRoleDao;
import com.ticketing.dao.SysUserRoleDao;
import com.ticketing.dao.UserDao;
import com.ticketing.entity.SysRole;
import com.ticketing.entity.SysUserRole;
import com.ticketing.entity.User;
import com.ticketing.service.AuthService;
import com.ticketing.service.EmailService;
import com.ticketing.service.UserService;
import com.ticketing.utils.*;
import com.ticketing.vo.LoginVo;
import com.ticketing.vo.RegisterVo;
import com.ticketing.vo.ResponseVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 用户服务实现类
 * <p>
 * 实现用户相关的核心业务逻辑，包括登录、注册、获取验证码、验证令牌等功能
 * </p>
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserDao userDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AuthService authService;

    @Autowired
    private SysUserRoleDao sysUserRoleDao;

    @Autowired
    private SysRoleDao sysRoleDao;

    /**
     * 用户登录
     * <p>
     * 根据提供的登录信息验证用户身份，包括检查用户是否存在、是否被锁定、登录失败次数，
     * 验证密码和验证码，最后生成JWT令牌并返回用户信息
     * </p>
     * @param loginVo 登录信息，包含手机号、密码、验证码等
     * @return 响应对象，包含JWT令牌和用户信息
     */
    @Override
    public ResponseVo login(LoginVo loginVo) {
        String phone = loginVo.getPhone();
        String email = loginVo.getEmail();
        String password = loginVo.getPassword();
        String encryptedPassword = loginVo.getEncryptedPassword();
        String verificationCode = loginVo.getVerificationCode();

        // 处理加密密码
        if (StringUtils.hasText(encryptedPassword)) {
            try {
                password = SM2Util.decrypt(encryptedPassword);
                log.info("SM2密码解密成功");
            } catch (Exception e) {
                log.error("SM2密码解密失败", e);
                return ResponseVo.error(400, "密码解密失败");
            }
        }

        User user = null;
        String loginIdentifier = null;

        // 判断是手机号登录还是邮箱登录
        if (StringUtils.hasText(email)) {
            // 邮箱登录
            user = userDao.selectByEmail(email);
            loginIdentifier = email;
        } else if (StringUtils.hasText(phone)) {
            // 手机号登录
            user = userDao.selectByPhone(phone);
            loginIdentifier = phone;
        } else {
            return ResponseVo.error(400, "请输入手机号或邮箱");
        }

        // 检查用户是否存在
        if (user == null) {
            return ResponseVo.error(400, "用户不存在");
        }

        // 验证密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return ResponseVo.error(400, "密码错误");
        }

        // 验证验证码
        if (!StringUtils.hasText(verificationCode)) {
            return ResponseVo.error(400, "验证码不能为空");
        }
        String codeId = loginVo.getCodeId();
        if (!StringUtils.hasText(codeId)) {
            return ResponseVo.error(400, "验证码ID不能为空");
        }
        String storedCode = (String) redisUtil.get("verification_code_" + codeId);
        if (!StringUtils.hasText(storedCode) || !storedCode.equalsIgnoreCase(verificationCode)) {
            return ResponseVo.error(400, "验证码错误");
        }
        redisUtil.delete("verification_code_" + codeId);

        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        userDao.updateById(user);

        // 生成JWT token
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("phone", user.getPhone());
        claims.put("email", user.getEmail());
        claims.put("nickname", user.getNickname());
        String token = jwtUtil.generateToken(claims);

        // 将token存储到Redis
        String tokenKey = "token:" + user.getId();
        redisUtil.set(tokenKey, token, 24 * 60 * 60);

        // 获取用户权限和角色
        List<String> permissions = authService.getUserPermissions(user.getId());
        List<String> roles = authService.getUserRoleCodes(user.getId());

        // 将权限和角色信息存储到Redis
        String permissionKey = "user:permissions:" + user.getId();
        String roleKey = "user:roles:" + user.getId();
        redisUtil.set(permissionKey, permissions, 24 * 60 * 60);
        redisUtil.set(roleKey, roles, 24 * 60 * 60);

        // 构建响应
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("permissions", permissions);
        data.put("roles", roles);
        
        // 对用户信息进行脱敏处理
        User maskedUser = new User();
        maskedUser.setId(user.getId());
        maskedUser.setPhone(DataMaskingUtil.maskPhone(user.getPhone()));
        maskedUser.setEmail(DataMaskingUtil.maskEmail(user.getEmail()));
        maskedUser.setNickname(user.getNickname());
        maskedUser.setStatus(user.getStatus());
        maskedUser.setCreateTime(user.getCreateTime());
        maskedUser.setLastLoginTime(user.getLastLoginTime());
        
        data.put("userInfo", maskedUser);

        return ResponseVo.success(data);
    }

    /**
     * 获取验证码
     * <p>
     * 生成验证码，存储到Redis，生成验证码图片并返回
     * </p>
     * @return 响应对象，包含验证码ID和图片URL
     */
    @Override
    public ResponseVo getVerificationCode() {
        // 生成验证码
        String code = VerificationCodeUtil.generateCode();
        String codeId = UUID.randomUUID().toString();

        // 存储验证码到Redis
        redisUtil.set("verification_code_" + codeId, code, 300);

        // 生成验证码图片
        String imageUrl = VerificationCodeUtil.generateImage(code);

        // 构建响应
        Map<String, Object> data = new HashMap<>();
        data.put("codeId", codeId);
        data.put("imageUrl", imageUrl);

        return ResponseVo.success(data);
    }

    /**
     * 验证令牌
     * <p>
     * 验证JWT令牌的有效性
     * </p>
     * @param token JWT令牌
     * @return 是否有效
     */
    @Override
    public boolean verifyToken(String token) {
        if (!jwtUtil.verifyToken(token)) {
            return false;
        }
        Long userId = jwtUtil.getUserIdFromToken(token);
        String tokenKey = "token:" + userId;
        String storedToken = (String) redisUtil.get(tokenKey);
        return storedToken != null && storedToken.equals(token);
    }

    @Override
    public ResponseVo logout(String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token);
            String tokenKey = "token:" + userId;
            redisUtil.delete(tokenKey);
            return ResponseVo.success(null);
        } catch (Exception e) {
            return ResponseVo.error(400, "登出失败");
        }
    }

    /**
     * 根据手机号获取用户
     * <p>
     * 从数据库根据手机号获取用户对象
     * </p>
     * @param phone 手机号
     * @return 用户对象
     */
    @Override
    public User getUserByPhone(String phone) {
        return userDao.selectByPhone(phone);
    }

    /**
     * 用户注册
     * <p>
     * 根据提供的注册信息创建新用户，包括检查用户是否已存在、验证验证码，
     * 加密密码并保存用户信息
     * </p>
     * @param registerVo 注册信息，包含手机号、密码、昵称、验证码等
     * @return 响应对象，包含注册结果
     */
    @Override
    public ResponseVo register(RegisterVo registerVo) {
        String phone = registerVo.getPhone();
        String email = registerVo.getEmail();
        String password = registerVo.getPassword();
        String nickname = registerVo.getNickname();
        String verificationCode = registerVo.getVerificationCode();

        // 判断是手机号注册还是邮箱注册
        if (StringUtils.hasText(email)) {
            // 邮箱注册（也需要手机号）
            if (!StringUtils.hasText(phone)) {
                return ResponseVo.error(400, "手机号不能为空");
            }

            // 检查手机号是否已被注册
            User existingUserByPhone = userDao.selectByPhone(phone);
            if (existingUserByPhone != null) {
                return ResponseVo.error(400, "该手机号已被注册");
            }

            // 检查邮箱是否已被注册
            User existingUserByEmail = userDao.selectByEmail(email);
            if (existingUserByEmail != null) {
                return ResponseVo.error(400, "该邮箱已被注册");
            }

            // 验证邮箱验证码
            if (!StringUtils.isEmpty(verificationCode)) {
                String storedCode = (String) redisUtil.get("email_verification_code_" + email);
                if (StringUtils.isEmpty(storedCode) || !storedCode.equals(verificationCode)) {
                    return ResponseVo.error(400, "邮箱验证码错误");
                }
                // 验证码使用后删除
                redisUtil.delete("email_verification_code_" + email);
            }

            // 创建新用户
            User user = new User();
            user.setPhone(phone);
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(password));
            user.setNickname(nickname);
            user.setStatus(1);
            user.setLoginFailCount(0);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());

            userDao.insert(user);
            assignNormalUserRole(user.getId());

        } else if (StringUtils.hasText(phone)) {
            // 手机号注册
            // 检查用户是否已存在
            User existingUser = userDao.selectByPhone(phone);
            if (existingUser != null) {
                return ResponseVo.error(400, "用户已存在");
            }

            // 验证验证码（必填）
            if (StringUtils.isEmpty(verificationCode)) {
                return ResponseVo.error(400, "验证码不能为空");
            }
            String codeId = registerVo.getCodeId();
            if (StringUtils.isEmpty(codeId)) {
                return ResponseVo.error(400, "验证码ID不能为空");
            }
            String storedCode = (String) redisUtil.get("verification_code_" + codeId);
            if (StringUtils.isEmpty(storedCode) || !storedCode.equalsIgnoreCase(verificationCode)) {
                return ResponseVo.error(400, "验证码错误");
            }
            redisUtil.delete("verification_code_" + codeId);

            // 创建新用户
            User user = new User();
            user.setPhone(phone);
            user.setPassword(passwordEncoder.encode(password));
            user.setNickname(nickname);
            user.setStatus(1); // 1-正常
            user.setLoginFailCount(0);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());

            // 保存用户
            userDao.insert(user);
            assignNormalUserRole(user.getId());

        } else {
            return ResponseVo.error(400, "手机号或邮箱不能为空");
        }

        return ResponseVo.success(null);
    }

    private void assignNormalUserRole(Long userId) {
        LambdaQueryWrapper<SysRole> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(SysRole::getRoleCode, "NORMAL_USER");
        SysRole normalUserRole = sysRoleDao.selectOne(roleWrapper);
        if (normalUserRole != null) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(normalUserRole.getId());
            userRole.setCreateTime(LocalDateTime.now());
            sysUserRoleDao.insert(userRole);
            log.info("用户 {} 已分配 NORMAL_USER 角色", userId);
        } else {
            log.warn("NORMAL_USER 角色不存在，无法为用户 {} 分配角色", userId);
        }
    }

    @Override
    public ResponseVo sendEmailVerificationCode(String email) {
        if (!StringUtils.hasText(email)) {
            return ResponseVo.error(400, "邮箱不能为空");
        }

        // 验证邮箱格式
        if (!email.matches("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$")) {
            return ResponseVo.error(400, "邮箱格式不正确");
        }

        // 检查邮箱是否已被注册
        User existingUser = userDao.selectByEmail(email);
        if (existingUser != null) {
            return ResponseVo.error(400, "该邮箱已被注册");
        }

        // 检查是否频繁发送验证码
        String sendKey = "email_code_send_" + email;
        if (redisUtil.exists(sendKey)) {
            return ResponseVo.error(400, "验证码发送过于频繁，请稍后再试");
        }

        // 生成6位验证码
        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));

        // 发送验证码邮件
        emailService.sendVerificationCode(email, code);

        // 存储验证码到Redis，5分钟有效期
        redisUtil.set("email_verification_code_" + email, code, 300);

        // 设置发送间隔限制
        redisUtil.set(sendKey, "1", 60);

        log.info("邮箱验证码已发送: {}", email);
        return ResponseVo.success("验证码已发送到您的邮箱");
    }

    @Override
    public User getUserByEmail(String email) {
        return userDao.selectByEmail(email);
    }

    @Override
    public ResponseVo getAllUsers() {
        return ResponseVo.success(userDao.selectList(null));
    }

    @Override
    public List<User> getAllUsersList() {
        return userDao.selectList(null);
    }

    @Override
    public User getUserById(Long userId) {
        return userDao.selectById(userId);
    }

    @Override
    public ResponseVo updateUserInfo(Long userId, String nickname, String email) {
        User user = userDao.selectById(userId);
        if (user == null) {
            return ResponseVo.error(400, "用户不存在");
        }

        if (StringUtils.hasText(nickname)) {
            user.setNickname(nickname);
        }

        if (StringUtils.hasText(email) && !email.equals(user.getEmail())) {
            User existingUser = userDao.selectByEmail(email);
            if (existingUser != null) {
                return ResponseVo.error(400, "该邮箱已被其他用户使用");
            }
            user.setEmail(email);
        }

        user.setUpdateTime(LocalDateTime.now());
        userDao.updateById(user);

        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("nickname", user.getNickname());
        data.put("phone", user.getPhone());
        data.put("email", user.getEmail());
        data.put("avatarUrl", user.getAvatarUrl());
        return ResponseVo.success(data);
    }

    @Override
    public ResponseVo changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userDao.selectById(userId);
        if (user == null) {
            return ResponseVo.error(400, "用户不存在");
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return ResponseVo.error(400, "原密码错误");
        }

        if (newPassword == null || newPassword.length() < 6) {
            return ResponseVo.error(400, "新密码长度不能少于6位");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userDao.updateById(user);

        String tokenKey = "token:" + userId;
        redisUtil.delete(tokenKey);

        return ResponseVo.success("密码修改成功，请重新登录");
    }

    @Override
    public ResponseVo updateAvatar(Long userId, String avatarUrl) {
        User user = userDao.selectById(userId);
        if (user == null) {
            return ResponseVo.error(400, "用户不存在");
        }

        user.setAvatarUrl(avatarUrl);
        user.setUpdateTime(LocalDateTime.now());
        userDao.updateById(user);

        return ResponseVo.success(avatarUrl);
    }

    @Override
    public ResponseVo bindPhone(Long userId, String phone, String verificationCode) {
        if (!StringUtils.hasText(phone)) {
            return ResponseVo.error(400, "手机号不能为空");
        }
        if (!StringUtils.hasText(verificationCode)) {
            return ResponseVo.error(400, "验证码不能为空");
        }

        User user = userDao.selectById(userId);
        if (user == null) {
            return ResponseVo.error(400, "用户不存在");
        }

        User existingUser = userDao.selectByPhone(phone);
        if (existingUser != null && !existingUser.getId().equals(userId)) {
            return ResponseVo.error(400, "该手机号已被其他用户绑定");
        }

        String storedCode = (String) redisUtil.get("bind_phone_code_" + phone);
        if (!StringUtils.hasText(storedCode) || !storedCode.equals(verificationCode)) {
            return ResponseVo.error(400, "验证码错误");
        }

        user.setPhone(phone);
        user.setUpdateTime(LocalDateTime.now());
        userDao.updateById(user);
        redisUtil.delete("bind_phone_code_" + phone);

        return ResponseVo.success("手机号绑定成功");
    }

    @Override
    public ResponseVo bindEmail(Long userId, String email, String verificationCode) {
        if (!StringUtils.hasText(email)) {
            return ResponseVo.error(400, "邮箱不能为空");
        }
        if (!StringUtils.hasText(verificationCode)) {
            return ResponseVo.error(400, "验证码不能为空");
        }

        User user = userDao.selectById(userId);
        if (user == null) {
            return ResponseVo.error(400, "用户不存在");
        }

        User existingUser = userDao.selectByEmail(email);
        if (existingUser != null && !existingUser.getId().equals(userId)) {
            return ResponseVo.error(400, "该邮箱已被其他用户绑定");
        }

        String storedCode = (String) redisUtil.get("email_verification_code_" + email);
        if (!StringUtils.hasText(storedCode) || !storedCode.equals(verificationCode)) {
            return ResponseVo.error(400, "验证码错误");
        }

        user.setEmail(email);
        user.setUpdateTime(LocalDateTime.now());
        userDao.updateById(user);
        redisUtil.delete("email_verification_code_" + email);

        return ResponseVo.success("邮箱绑定成功");
    }

    @Override
    public ResponseVo unbindPhone(Long userId) {
        User user = userDao.selectById(userId);
        if (user == null) {
            return ResponseVo.error(400, "用户不存在");
        }

        if (!StringUtils.hasText(user.getEmail())) {
            return ResponseVo.error(400, "请先绑定邮箱，否则解绑手机后将无法登录");
        }

        user.setPhone(null);
        user.setUpdateTime(LocalDateTime.now());
        userDao.updateById(user);

        return ResponseVo.success("手机号解绑成功");
    }

    @Override
    public ResponseVo unbindEmail(Long userId) {
        User user = userDao.selectById(userId);
        if (user == null) {
            return ResponseVo.error(400, "用户不存在");
        }

        if (!StringUtils.hasText(user.getPhone())) {
            return ResponseVo.error(400, "请先绑定手机号，否则解绑邮箱后将无法登录");
        }

        user.setEmail(null);
        user.setUpdateTime(LocalDateTime.now());
        userDao.updateById(user);

        return ResponseVo.success("邮箱解绑成功");
    }
}
