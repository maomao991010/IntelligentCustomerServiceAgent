-- =====================================================
-- 退款、电子票、订单扩展 - 数据初始化脚本
-- 注意：表结构已在 ticketing_system.sql 中定义
-- 本脚本仅负责插入权限数据
-- =====================================================

-- 添加退款审核权限（如果不存在）
INSERT INTO `sys_permission` (`permission_code`, `permission_name`, `module`, `type`, `status`)
SELECT 'refund:audit', '退款审核', '订单', 'BUTTON', 1
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'refund:audit'
);

-- 添加退款查看权限（如果不存在）
INSERT INTO `sys_permission` (`permission_code`, `permission_name`, `module`, `type`, `status`)
SELECT 'refund:view', '退款查看', '订单', 'BUTTON', 1
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'refund:view'
);

-- 为管理员角色添加退款审核权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ADMIN' AND p.permission_code = 'refund:audit'
AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

-- 为管理员角色添加退款查看权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ADMIN' AND p.permission_code = 'refund:view'
AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
);
