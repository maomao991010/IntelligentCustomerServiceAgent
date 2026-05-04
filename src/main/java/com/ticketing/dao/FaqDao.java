package com.ticketing.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticketing.entity.Faq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FaqDao extends BaseMapper<Faq> {

    @Select("SELECT * FROM faq WHERE status = 'ACTIVE' ORDER BY sort_order ASC, create_time DESC")
    List<Faq> selectAllActive();

    @Select("SELECT * FROM faq WHERE status = 'ACTIVE' AND category = #{category} ORDER BY sort_order ASC")
    List<Faq> selectByCategory(@Param("category") String category);
}
