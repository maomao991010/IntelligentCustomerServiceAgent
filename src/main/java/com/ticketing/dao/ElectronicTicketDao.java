package com.ticketing.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticketing.entity.ElectronicTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ElectronicTicketDao extends BaseMapper<ElectronicTicket> {

    @Select("SELECT * FROM electronic_ticket WHERE ticket_code = #{ticketCode}")
    ElectronicTicket selectByTicketCode(@Param("ticketCode") String ticketCode);

    @Select("SELECT * FROM electronic_ticket WHERE order_id = #{orderId}")
    ElectronicTicket selectByOrderId(@Param("orderId") String orderId);
}
