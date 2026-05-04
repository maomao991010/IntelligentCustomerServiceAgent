package com.ticketing.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticketing.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatHistoryDao extends BaseMapper<ChatHistory> {

    @Select("SELECT * FROM chat_history WHERE session_id = #{sessionId} ORDER BY create_time ASC")
    List<ChatHistory> selectBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM chat_history WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT 50")
    List<ChatHistory> selectByUserId(@Param("userId") Long userId);
}
