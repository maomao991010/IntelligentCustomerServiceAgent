package com.ticketing.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticketing.entity.OperationLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperationLogDao extends BaseMapper<OperationLogEntity> {
    IPage<OperationLogEntity> selectLogPage(Page<OperationLogEntity> page,
                                            @Param("userId") Long userId,
                                            @Param("module") String module,
                                            @Param("type") String type,
                                            @Param("startTime") String startTime,
                                            @Param("endTime") String endTime);
}
