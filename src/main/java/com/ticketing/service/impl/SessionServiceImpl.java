package com.ticketing.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticketing.dao.SessionDao;
import com.ticketing.dao.ActivityDao;
import com.ticketing.entity.Session;
import com.ticketing.entity.Activity;
import com.ticketing.service.SessionService;
import com.ticketing.utils.RedisUtil;
import com.ticketing.vo.PageResultVo;
import com.ticketing.vo.ResponseVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 场次服务实现类
 * 处理场次相关的业务逻辑，包括场次列表查询、场次详情查询、剩余座位更新等
 */
@Service
@Slf4j
public class SessionServiceImpl implements SessionService {

    @Autowired
    private SessionDao sessionDao;

    @Autowired
    private ActivityDao activityDao;

    @Autowired
    private RedisUtil redisUtil;

    /**
     * 获取场次列表
     * @param activityId 活动ID，可选参数，不传递则查询所有场次
     * @return 场次列表，成功返回场次数据，失败返回错误信息
     */
    @Override
    public ResponseVo getSessionList(Long activityId) {
        try {
            // 尝试从Redis缓存获取
            String cacheKey = "session_list_" + (activityId != null ? activityId : "all");
            List<Session> sessions = (List<Session>) redisUtil.get(cacheKey);
            
            // 检查sessions是否为null或空列表
            if (sessions != null && !sessions.isEmpty()) {
                return ResponseVo.success(sessions);
            }

            // 从数据库查询
            if (activityId != null) {
                // 根据活动ID查询场次
                sessions = sessionDao.selectByActivityId(activityId);
            } else {
                // 当activityId为null时，查询所有场次
                sessions = sessionDao.selectList(null);
            }

            // 为每个场次设置艺人信息
            for (Session session : sessions) {
                if (session.getArtist() == null || session.getArtist().isEmpty()) {
                    // 从活动表获取艺人信息
                    Activity activity = activityDao.selectById(session.getActivityId());
                    if (activity != null && activity.getArtist() != null) {
                        session.setArtist(activity.getArtist());
                    }
                }
            }

            // 缓存到Redis，有效期30分钟
            redisUtil.set(cacheKey, sessions, 1800);

            return ResponseVo.success(sessions);
        } catch (Exception e) {
            log.error("获取场次列表失败", e);
            return ResponseVo.error(500, "获取场次列表失败");
        }
    }

    /**
     * 获取场次详情
     * @param sessionId 场次ID
     * @return 场次详情，成功返回场次数据，失败返回错误信息
     */
    @Override
    public ResponseVo getSessionDetail(Long sessionId) {
        try {
            // 尝试从Redis缓存获取
            String cacheKey = "session_detail_" + sessionId;
            Session session = (Session) redisUtil.get(cacheKey);
            if (session != null) {
                return ResponseVo.success(session);
            }

            // 从数据库查询
            session = sessionDao.selectById(sessionId);
            if (session == null) {
                return ResponseVo.error(400, "场次不存在");
            }

            // 设置艺人信息
            if (session.getArtist() == null || session.getArtist().isEmpty()) {
                // 从活动表获取艺人信息
                Activity activity = activityDao.selectById(session.getActivityId());
                if (activity != null && activity.getArtist() != null) {
                    session.setArtist(activity.getArtist());
                }
            }

            // 缓存到Redis，有效期30分钟
            redisUtil.set(cacheKey, session, 1800);

            return ResponseVo.success(session);
        } catch (Exception e) {
            log.error("获取场次详情失败", e);
            return ResponseVo.error(500, "获取场次详情失败");
        }
    }

    /**
     * 根据ID获取场次信息
     * @param sessionId 场次ID
     * @return 场次信息，不存在返回null
     */
    @Override
    public Session getSessionById(Long sessionId) {
        // 尝试从Redis缓存获取
        String cacheKey = "session_detail_" + sessionId;
        Session session = (Session) redisUtil.get(cacheKey);
        if (session != null) {
            return session;
        }

        // 从数据库查询
        session = sessionDao.selectById(sessionId);
        if (session != null) {
            // 缓存到Redis，有效期30分钟
            redisUtil.set(cacheKey, session, 1800);
        }

        return session;
    }

    /**
     * 更新剩余座位数
     * @param sessionId 场次ID
     * @param count 减少的座位数
     */
    @Override
    public void updateRemainingSeats(Long sessionId, Integer count) {
        try {
            Session session = sessionDao.selectById(sessionId);
            if (session != null) {
                // 第一次删除缓存（延迟双删 - 前置删除）
                String cacheKey = "session_detail_" + sessionId;
                redisUtil.delete(cacheKey);
                redisUtil.delete("session_list_all");
                redisUtil.delete("session_list_" + session.getActivityId());

                // 计算新的剩余座位数
                int newRemainingSeats = session.getRemainingSeats() - count;
                session.setRemainingSeats(newRemainingSeats);
                sessionDao.updateById(session);

                // 第二次删除缓存（延迟双删 - 后置延迟删除）
                delayDeleteCache(session.getActivityId(), sessionId);
            }
        } catch (Exception e) {
            log.error("更新剩余座位失败", e);
        }
    }

    /**
     * 添加场次
     * @param session 场次信息
     * @return 操作结果，成功返回添加的场次数据，失败返回错误信息
     */
    @Override
    public ResponseVo addSession(Session session) {
        try {
            // 第一次删除缓存（延迟双删 - 前置删除）
            redisUtil.delete("session_list_all");
            if (session.getActivityId() != null) {
                redisUtil.delete("session_list_" + session.getActivityId());
            }

            // 设置默认值
            session.setStatus("ACTIVE");
            session.setCreateTime(java.time.LocalDateTime.now());
            session.setUpdateTime(java.time.LocalDateTime.now());
            session.setRemainingSeats(session.getTotalSeats());

            // 保存到数据库
            sessionDao.insert(session);

            // 第二次删除缓存（延迟双删 - 后置延迟删除）
            delayDeleteCache(session.getActivityId(), null);

            return ResponseVo.success(session);
        } catch (Exception e) {
            log.error("添加场次失败", e);
            return ResponseVo.error(500, "添加场次失败");
        }
    }

    /**
     * 更新场次
     * @param session 场次信息
     * @return 操作结果，成功返回更新后的场次数据，失败返回错误信息
     */
    @Override
    public ResponseVo updateSession(Session session) {
        try {
            // 检查场次是否存在
            Session existingSession = sessionDao.selectById(session.getId());
            if (existingSession == null) {
                return ResponseVo.error(400, "场次不存在");
            }

            // 第一次删除缓存（延迟双删 - 前置删除）
            String cacheKey = "session_detail_" + session.getId();
            redisUtil.delete(cacheKey);
            redisUtil.delete("session_list_all");
            if (session.getActivityId() != null) {
                redisUtil.delete("session_list_" + session.getActivityId());
            }
            if (existingSession.getActivityId() != null && !existingSession.getActivityId().equals(session.getActivityId())) {
                redisUtil.delete("session_list_" + existingSession.getActivityId());
            }

            // 更新时间
            session.setUpdateTime(java.time.LocalDateTime.now());

            // 保存到数据库
            sessionDao.updateById(session);

            // 第二次删除缓存（延迟双删 - 后置延迟删除）
            delayDeleteCache(session.getActivityId(), session.getId());

            return ResponseVo.success(session);
        } catch (Exception e) {
            log.error("更新场次失败", e);
            return ResponseVo.error(500, "更新场次失败");
        }
    }

    /**
     * 删除场次
     * @param sessionId 场次ID
     * @return 操作结果，成功返回成功信息，失败返回错误信息
     */
    @Override
    public ResponseVo deleteSession(Long sessionId) {
        try {
            // 检查场次是否存在
            Session existingSession = sessionDao.selectById(sessionId);
            if (existingSession == null) {
                return ResponseVo.error(400, "场次不存在");
            }

            // 第一次删除缓存（延迟双删 - 前置删除）
            String cacheKey = "session_detail_" + sessionId;
            redisUtil.delete(cacheKey);
            redisUtil.delete("session_list_all");
            if (existingSession.getActivityId() != null) {
                redisUtil.delete("session_list_" + existingSession.getActivityId());
            }

            // 删除数据库记录
            sessionDao.deleteById(sessionId);

            // 第二次删除缓存（延迟双删 - 后置延迟删除）
            delayDeleteCache(existingSession.getActivityId(), sessionId);

            return ResponseVo.success("删除成功");
        } catch (Exception e) {
            log.error("删除场次失败", e);
            return ResponseVo.error(500, "删除场次失败");
        }
    }
    
    @Override
    public ResponseVo getSessionPage(int pageNum, int pageSize, String keyword) {
        try {
            Page<Session> page = new Page<>(pageNum, pageSize);
            IPage<Session> sessionPage = sessionDao.selectSessionPageWithSearch(page, keyword);
            
            PageResultVo<Session> pageResult = new PageResultVo<>();
            pageResult.setRecords(sessionPage.getRecords());
            pageResult.setTotal(sessionPage.getTotal());
            pageResult.setPageNum((int) sessionPage.getCurrent());
            pageResult.setPageSize((int) sessionPage.getSize());
            pageResult.setPages((int) sessionPage.getPages());
            
            return ResponseVo.success(pageResult);
        } catch (Exception e) {
            log.error("获取场次分页列表失败", e);
            return ResponseVo.error(500, "获取场次列表失败");
        }
    }

    /**
     * 延迟删除缓存（延迟双删策略）
     * @param activityId 活动ID
     * @param sessionId 场次ID
     */
    private void delayDeleteCache(Long activityId, Long sessionId) {
        new Thread(() -> {
            try {
                Thread.sleep(500);
                if (sessionId != null) {
                    redisUtil.delete("session_detail_" + sessionId);
                }
                redisUtil.delete("session_list_all");
                if (activityId != null) {
                    redisUtil.delete("session_list_" + activityId);
                }
                log.info("延迟双删缓存完成 - activityId: {}, sessionId: {}", activityId, sessionId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("延迟双删缓存失败", e);
            }
        }).start();
    }
}
