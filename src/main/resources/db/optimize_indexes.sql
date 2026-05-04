-- ============================================================
-- 票务系统 数据库索引优化脚本
-- 优化日期: 2026-04-18
-- 说明: 基于实际业务查询分析，添加必要的索引以提升查询性能
-- ============================================================

-- 1. 用户表索引
-- 登录时按手机号/邮箱查询，需唯一约束防止重复注册
ALTER TABLE `user`
    ADD UNIQUE INDEX uk_phone (phone),
    ADD UNIQUE INDEX uk_email (email);

-- 2. 订单表索引（核心业务表，高频查询）
-- 按订单号查询订单详情
ALTER TABLE `order`
    ADD UNIQUE INDEX uk_order_id (order_id);
-- 按锁定订单ID查询（创建/取消订单场景）
ALTER TABLE `order`
    ADD INDEX idx_lock_order_id (lock_order_id);
-- 按用户ID查询订单列表（用户中心，最常用，复合索引覆盖排序）
ALTER TABLE `order`
    ADD INDEX idx_user_create_time (user_id, create_time DESC);
-- 按状态查询（后台管理）
ALTER TABLE `order`
    ADD INDEX idx_order_status (order_status);
-- 场次关联查询（订单搜索JOIN session）
ALTER TABLE `order`
    ADD INDEX idx_session_id (session_id);

-- 3. 座位表索引
-- 按场次查询座位图（选座核心操作），复合索引覆盖状态筛选
ALTER TABLE seat
    ADD INDEX idx_session_status (session_id, status);
-- 座位锁定查询
ALTER TABLE seat
    ADD INDEX idx_lock_user (lock_user_id, lock_time);

-- 4. 场次表索引
-- 按活动ID查询场次列表（活动详情页）
ALTER TABLE session
    ADD INDEX idx_activity_id (activity_id);
-- 状态筛选（前台展示可用场次）
ALTER TABLE session
    ADD INDEX idx_session_status (status);
-- 复合索引：活动+状态（常用组合查询）
ALTER TABLE session
    ADD INDEX idx_activity_status (activity_id, status);

-- 5. FAQ表索引
-- 前台展示活跃FAQ（按排序字段排序）
ALTER TABLE faq
    ADD INDEX idx_faq_active (status, sort_order);
-- 按分类查询FAQ
ALTER TABLE faq
    ADD INDEX idx_faq_category (category, status, sort_order);

-- 6. 聊天历史表索引（高频写入+查询）
-- 按会话ID查询聊天记录（时间正序）
ALTER TABLE chat_history
    ADD INDEX idx_chat_session (session_id, create_time);
-- 按用户ID查询最近记录（时间倒序，LIMIT 50）
ALTER TABLE chat_history
    ADD INDEX idx_chat_user (user_id, create_time DESC);
-- FAQ关联查询
ALTER TABLE chat_history
    ADD INDEX idx_chat_faq (faq_id);

-- 7. 权限系统索引（每次请求都会查询）
-- 用户角色关联
ALTER TABLE sys_user_role
    ADD INDEX idx_ur_user (user_id),
    ADD INDEX idx_ur_role (role_id);
-- 角色权限关联
ALTER TABLE sys_role_permission
    ADD INDEX idx_rp_role (role_id),
    ADD INDEX idx_rp_permission (permission_id);
-- 权限编码查询（权限校验高频调用）
ALTER TABLE sys_permission
    ADD INDEX idx_perm_code (permission_code);
-- 角色编码查询
ALTER TABLE sys_role
    ADD INDEX idx_role_code (role_code);

-- 8. 活动表索引
-- 活动列表查询
ALTER TABLE activity
    ADD INDEX idx_activity_status (status);

-- ============================================================
-- 索引使用情况验证（执行后可运行以下SQL检查效果）
-- ============================================================
-- SHOW INDEX FROM `user`;
-- SHOW INDEX FROM `order`;
-- SHOW INDEX FROM seat;
-- SHOW INDEX FROM session;
-- SHOW INDEX FROM faq;
-- SHOW INDEX FROM chat_history;

-- 查看当前慢查询日志配置
-- SHOW VARIABLES LIKE 'slow_query_log%';
-- SHOW VARIABLES LIKE 'long_query_time';
