package com.ticketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ticketing.dao.SeatDao;
import com.ticketing.entity.Seat;
import com.ticketing.service.SeatService;
import com.ticketing.utils.RedisUtil;
import com.ticketing.vo.ResponseVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 座位服务实现类
 * <p>
 * 实现座位相关的核心业务逻辑，包括获取座位图、获取场次座位列表、更新座位状态等功能
 * </p>
 */
@Service
@Slf4j
public class SeatServiceImpl implements SeatService {

    @Autowired
    private SeatDao seatDao;

    @Autowired
    private RedisUtil redisUtil;

    /**
     * 获取座位图
     * <p>
     * 根据场次ID获取该场次的座位分布和状态信息，并按行号分组
     * </p>
     * @param sessionId 场次ID
     * @return 响应对象，包含按行号分组的座位信息
     */
    @Override
    public ResponseVo getSeatMap(Long sessionId) {
        try {
            // 尝试从Redis缓存获取
            String cacheKey = "seat_map_" + sessionId;
            List<Seat> seats = (List<Seat>) redisUtil.get(cacheKey);
            if (seats == null) {
                // 从数据库查询
                seats = seatDao.selectBySessionId(sessionId);
                // 缓存到Redis，有效期30分钟
                redisUtil.set(cacheKey, seats, 1800);
            }

            // 按行号分组
            Map<Integer, List<Seat>> seatMap = seats.stream()
                    .collect(Collectors.groupingBy(Seat::getRowNumber));

            return ResponseVo.success(seatMap);
        } catch (Exception e) {
            log.error("获取座位图失败", e);
            return ResponseVo.error(500, "获取座位图失败");
        }
    }

    /**
     * 根据场次ID获取座位列表
     * <p>
     * 从缓存或数据库获取指定场次的所有座位信息
     * </p>
     * @param sessionId 场次ID
     * @return 座位列表
     */
    @Override
    public List<Seat> getSeatsBySessionId(Long sessionId) {
        // 尝试从Redis缓存获取
        String cacheKey = "seat_map_" + sessionId;
        List<Seat> seats = (List<Seat>) redisUtil.get(cacheKey);
        if (seats == null) {
            // 从数据库查询
            seats = seatDao.selectBySessionId(sessionId);
            // 缓存到Redis，有效期30分钟
            redisUtil.set(cacheKey, seats, 1800);
        }
        return seats;
    }

    /**
     * 更新座位状态
     * <p>
     * 更新指定座位的状态，并清除相关缓存
     * </p>
     * @param seatId 座位ID
     * @param status 座位状态
     */
    @Override
    public void updateSeatStatus(Long seatId, String status) {
        try {
            Seat seat = seatDao.selectById(seatId);
            if (seat != null) {
                seat.setStatus(status);
                seatDao.updateById(seat);

                // 更新缓存
                String cacheKey = "seat_map_" + seat.getSessionId();
                redisUtil.delete(cacheKey);
            }
        } catch (Exception e) {
            log.error("更新座位状态失败", e);
        }
    }

    /**
     * 批量更新座位状态
     * <p>
     * 批量更新多个座位的状态，并设置锁定用户信息（如果状态为锁定），最后清除相关缓存
     * </p>
     * @param seatIds 座位ID列表
     * @param status 座位状态
     * @param userId 用户ID
     */
    @Override
    public void updateSeatStatusBatch(List<Long> seatIds, String status, String userId) {
        try {
            for (Long seatId : seatIds) {
                try {
                    Seat seat = seatDao.selectById(seatId);
                    if (seat != null) {
                        seat.setStatus(status);
                        if ("LOCKED".equals(status)) {
                            seat.setLockUserId(userId);
                            seat.setLockTime(LocalDateTime.now());
                        } else {
                            seat.setLockUserId(null);
                            seat.setLockTime(null);
                        }
                        seatDao.updateById(seat);
                    } else {
                        // 座位不存在，可能是前端生成的模拟数据
                        log.warn("座位不存在: {}", seatId);
                    }
                } catch (Exception e) {
                    log.error("更新座位状态失败: {}", seatId, e);
                    // 继续处理下一个座位，不影响整体操作
                }
            }

            // 清除缓存
            if (!seatIds.isEmpty()) {
                try {
                    Seat firstSeat = seatDao.selectById(seatIds.get(0));
                    if (firstSeat != null) {
                        String cacheKey = "seat_map_" + firstSeat.getSessionId();
                        redisUtil.delete(cacheKey);
                    }
                } catch (Exception e) {
                    log.error("清除缓存失败", e);
                }
            }
        } catch (Exception e) {
            log.error("批量更新座位状态失败", e);
        }
    }

    /**
     * 根据ID获取座位
     * <p>
     * 从数据库根据座位ID获取座位对象
     * </p>
     * @param seatId 座位ID
     * @return 座位对象
     */
    @Override
    public Seat getSeatById(Long seatId) {
        return seatDao.selectById(seatId);
    }

    /**
     * 生成座位
     * <p>
     * 根据场次ID和总座位数生成座位信息
     * </p>
     * @param sessionId 场次ID
     * @param totalSeats 总座位数
     * @return 响应对象
     */
    @Override
    public ResponseVo generateSeats(Long sessionId, int totalSeats) {
        try {
            // 先删除该场次的所有座位
            seatDao.deleteBySessionId(sessionId);
            
            // 生成新座位
            int rowCount = (totalSeats + 9) / 10; // 每行10个座位
            int seatNumber = 1;
            LocalDateTime now = LocalDateTime.now();
            
            for (int row = 1; row <= rowCount; row++) {
                int seatsInRow = Math.min(10, totalSeats - (row - 1) * 10);
                for (int col = 1; col <= seatsInRow; col++) {
                    Seat seat = new Seat();
                    seat.setSessionId(sessionId);
                    seat.setRowNumber(row);
                    seat.setSeatNumber(col);
                    seat.setSeatType("STANDARD");
                    seat.setPrice(100); // 默认价格
                    seat.setStatus("AVAILABLE");
                    seat.setCreateTime(now);
                    seat.setUpdateTime(now);
                    seatDao.insert(seat);
                    seatNumber++;
                }
            }
            
            // 清除缓存
            String cacheKey = "seat_map_" + sessionId;
            redisUtil.delete(cacheKey);
            
            return ResponseVo.success("座位生成成功");
        } catch (Exception e) {
            log.error("生成座位失败", e);
            return ResponseVo.error(500, "生成座位失败");
        }
    }
}
