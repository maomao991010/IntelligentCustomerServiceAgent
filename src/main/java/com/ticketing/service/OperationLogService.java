package com.ticketing.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ticketing.entity.OperationLogEntity;

public interface OperationLogService {
    void saveLog(OperationLogEntity logEntity);
    IPage<OperationLogEntity> getLogPage(int pageNum, int pageSize, Long userId, String module, String type, String startTime, String endTime);
}
