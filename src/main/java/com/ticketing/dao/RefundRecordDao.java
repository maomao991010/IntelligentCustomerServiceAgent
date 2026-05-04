package com.ticketing.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticketing.entity.RefundRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefundRecordDao extends BaseMapper<RefundRecord> {
}
