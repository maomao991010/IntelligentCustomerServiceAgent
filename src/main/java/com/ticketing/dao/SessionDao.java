package com.ticketing.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticketing.entity.Session;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SessionDao extends BaseMapper<Session> {
    List<Session> selectByActivityId(Long activityId);
    Session selectById(Long id);
    
    IPage<Session> selectSessionPageWithSearch(Page<Session> page, @Param("keyword") String keyword);
    
    IPage<Session> selectSessionPageWithArtistAndLocation(Page<Session> page, @Param("artist") String artist, @Param("location") String location);
}
