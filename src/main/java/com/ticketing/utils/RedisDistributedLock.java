package com.ticketing.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Redis分布式锁实现类
 * <p>
 * 支持可重入锁、自动续期（看门狗机制）、原子操作
 * 使用Lua脚本保证释放锁和续期的原子性
 * </p>
 */
@Component
@Slf4j
public class RedisDistributedLock {

    // Redis模板，用于操作Redis
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 锁前缀，用于Redis键名
    private static final String LOCK_PREFIX = "distributed_lock_";
    // 默认锁过期时间（秒）
    private static final long DEFAULT_LOCK_EXPIRE = 30;
    // 续期过期时间（秒）
    private static final long RENEW_EXPIRE_TIME = 10;
    // 续期检查间隔（秒）
    private static final long RENEW_CHECK_INTERVAL = 10;

    // 续期线程映射，用于管理看门狗线程
    private final Map<String, Thread> renewThreadMap = new ConcurrentHashMap<>();
    // 重入计数映射，用于实现可重入锁
    private final Map<String, Integer> reentrantCountMap = new ConcurrentHashMap<>();

    // 释放锁的Lua脚本
    // 原子操作：检查锁是否被当前线程持有，是则删除
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "    return redis.call('del', KEYS[1]) " +
                    "else " +
                    "    return 0 " +
                    "end";

    // 续期锁的Lua脚本
    // 原子操作：检查锁是否被当前线程持有，是则续期
    private static final String RENEW_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "    return redis.call('expire', KEYS[1], ARGV[2]) " +
                    "else " +
                    "    return 0 " +
                    "end";

    /**
     * 获取分布式锁（可重入）
     * @param key 锁键
     * @param value 锁值（建议使用线程ID或UUID）
     * @return 是否获取成功
     */
    public boolean lock(String key, String value) {
        // 调用带过期时间的获取锁方法
        return lock(key, value, DEFAULT_LOCK_EXPIRE);
    }

    /**
     * 获取分布式锁（可重入）
     * @param key 锁键
     * @param value 锁值
     * @param expireSeconds 锁过期时间（秒）
     * @return 是否获取成功
     */
    public boolean lock(String key, String value, long expireSeconds) {
        // 构建完整的锁键
        String lockKey = LOCK_PREFIX + key;
        // 获取当前线程ID
        String threadId = getThreadId();

        // 检查锁是否被当前线程持有（可重入）
        if (isLockedByCurrentThread(lockKey, value)) {
            // 获取重入计数
            Integer count = reentrantCountMap.get(lockKey);
            // 更新重入计数
            reentrantCountMap.put(lockKey, count == null ? 1 : count + 1);
            // 记录日志
            log.info("线程 {} 重入锁 {}，重入次数: {}", threadId, key, reentrantCountMap.get(lockKey));
            return true;
        }

        // 尝试获取锁（setIfAbsent 是原子操作）
        Boolean result = redisTemplate.opsForValue().setIfAbsent(lockKey, value, expireSeconds, TimeUnit.SECONDS);
        // 获取锁成功
        if (result != null && result) {
            // 初始化重入计数
            reentrantCountMap.put(lockKey, 1);
            // 启动看门狗线程（自动续期）
            startRenewThread(lockKey, value, expireSeconds);
            // 记录日志
            log.info("线程 {} 获取锁 {} 成功，过期时间: {}秒", threadId, key, expireSeconds);
            return true;
        }

        // 获取锁失败
        return false;
    }

    /**
     * 释放分布式锁（可重入）
     * @param key 锁键
     * @param value 锁值
     */
    public void unlock(String key, String value) {
        // 构建完整的锁键
        String lockKey = LOCK_PREFIX + key;
        // 获取当前线程ID
        String threadId = getThreadId();

        // 获取重入计数
        Integer count = reentrantCountMap.get(lockKey);
        // 重入次数大于1，减少重入计数
        if (count != null && count > 1) {
            // 更新重入计数
            reentrantCountMap.put(lockKey, count - 1);
            // 记录日志
            log.info("线程 {} 释放锁 {}，剩余重入次数: {}", threadId, key, count - 1);
            return;
        }

        // 构建释放锁的脚本
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
        // 执行释放锁操作（原子操作）
        Long result = redisTemplate.execute(script, Collections.singletonList(lockKey), value);

        // 释放锁成功
        if (result != null && result > 0) {
            log.info("线程 {} 释放锁 {} 成功", threadId, key);
        } else {
            log.warn("线程 {} 释放锁 {} 失败，锁不存在或已被其他线程持有", threadId, key);
        }

        // 清除重入计数
        reentrantCountMap.remove(lockKey);
        // 停止看门狗线程
        stopRenewThread(lockKey);
    }

    /**
     * 尝试获取分布式锁（带重试机制）
     * @param key 锁键
     * @param value 锁值
     * @param retryCount 重试次数
     * @param retryInterval 重试间隔（毫秒）
     * @return 是否获取成功
     */
    public boolean tryLock(String key, String value, int retryCount, long retryInterval) {
        // 调用带过期时间的尝试获取锁方法
        return tryLock(key, value, DEFAULT_LOCK_EXPIRE, retryCount, retryInterval);
    }

    /**
     * 尝试获取分布式锁（带重试机制和可重入）
     * @param key 锁键
     * @param value 锁值
     * @param expireSeconds 锁过期时间（秒）
     * @param retryCount 重试次数
     * @param retryInterval 重试间隔（毫秒）
     * @return 是否获取成功
     */
    public boolean tryLock(String key, String value, long expireSeconds, int retryCount, long retryInterval) {
        // 尝试获取锁
        if (lock(key, value, expireSeconds)) {
            return true;
        }

        // 重试获取锁
        int count = 0;
        while (count < retryCount) {
            try {
                // 等待重试间隔
                Thread.sleep(retryInterval);
                // 尝试获取锁
                if (lock(key, value, expireSeconds)) {
                    return true;
                }
                // 增加重试计数
                count++;
            } catch (InterruptedException e) {
                // 恢复中断状态
                Thread.currentThread().interrupt();
                return false;
            }
        }

        // 重试失败
        return false;
    }

    /**
     * 检查指定锁是否被当前线程持有
     * @param lockKey 完整的锁键
     * @param value 锁值
     * @return 是否被当前线程持有
     */
    private boolean isLockedByCurrentThread(String lockKey, String value) {
        // 获取锁的当前值
        Object currentValue = redisTemplate.opsForValue().get(lockKey);
        // 检查值是否匹配
        return currentValue != null && currentValue.toString().equals(value);
    }

    /**
     * 获取当前线程ID
     * @return 线程ID字符串
     */
    private String getThreadId() {
        return Thread.currentThread().getId() + "";
    }

    /**
     * 启动看门狗线程（自动续期）
     * @param lockKey 完整的锁键
     * @param value 锁值
     * @param expireSeconds 锁过期时间（秒）
     */
    private void startRenewThread(String lockKey, String value, long expireSeconds) {
        // 检查是否已存在续期线程
        if (renewThreadMap.containsKey(lockKey)) {
            return;
        }

        // 创建续期线程
        Thread renewThread = new Thread(() -> {
            // 线程未中断时持续执行
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 等待续期检查间隔
                    Thread.sleep(RENEW_CHECK_INTERVAL * 1000);
                    // 执行续期操作
                    renewLock(lockKey, value, expireSeconds);
                } catch (InterruptedException e) {
                    // 恢复中断状态
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        // 设置为守护线程
        renewThread.setDaemon(true);
        // 设置线程名称
        renewThread.setName("lock-renew-" + lockKey);
        // 启动线程
        renewThread.start();
        // 记录续期线程
        renewThreadMap.put(lockKey, renewThread);
    }

    /**
     * 停止看门狗线程
     * @param lockKey 完整的锁键
     */
    private void stopRenewThread(String lockKey) {
        // 移除并获取续期线程
        Thread thread = renewThreadMap.remove(lockKey);
        // 中断线程
        if (thread != null) {
            thread.interrupt();
        }
    }

    /**
     * 续期锁（使用Lua脚本原子操作）
     * @param lockKey 完整的锁键
     * @param value 锁值
     * @param expireSeconds 锁过期时间（秒）
     */
    private void renewLock(String lockKey, String value, long expireSeconds) {
        try {
            // 构建续期脚本
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(RENEW_SCRIPT, Long.class);
            // 执行续期操作（原子操作）
            Long result = redisTemplate.execute(script, Collections.singletonList(lockKey), value, String.valueOf(expireSeconds));
            // 续期成功
            if (result != null && result > 0) {
                log.debug("锁 {} 自动续期成功，新过期时间: {}秒", lockKey, expireSeconds);
            }
        } catch (Exception e) {
            // 续期失败
            log.error("锁 {} 自动续期失败", lockKey, e);
        }
    }

    /**
     * 强制删除锁（用于测试或异常情况）
     * @param key 锁键
     */
    public void forceUnlock(String key) {
        // 构建完整的锁键
        String lockKey = LOCK_PREFIX + key;
        // 删除锁
        redisTemplate.delete(lockKey);
        // 清除重入计数
        reentrantCountMap.remove(lockKey);
        // 停止看门狗线程
        stopRenewThread(lockKey);
        // 记录日志
        log.info("强制删除锁 {}", key);
    }

    /**
     * 获取锁的剩余过期时间
     * @param key 锁键
     * @return 剩余过期时间（秒）
     */
    public Long getLockExpire(String key) {
        // 构建完整的锁键
        String lockKey = LOCK_PREFIX + key;
        // 获取剩余过期时间
        return redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
    }

    /**
     * 检查锁是否存在
     * @param key 锁键
     * @return 是否存在
     */
    public boolean isLocked(String key) {
        // 构建完整的锁键
        String lockKey = LOCK_PREFIX + key;
        // 检查锁是否存在
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }
}