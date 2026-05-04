package com.ticketing.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticketing.entity.Order;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrderDao extends BaseMapper<Order> {
    Order selectByOrderId(String orderId);
    Order selectByLockOrderId(String lockOrderId);
    List<Order> selectByUserId(Long userId);
    List<Order> selectByStatus(String status);
    
    IPage<Order> selectOrderPageWithSearch(Page<Order> page, @Param("userId") Long userId, @Param("keyword") String keyword);
}
