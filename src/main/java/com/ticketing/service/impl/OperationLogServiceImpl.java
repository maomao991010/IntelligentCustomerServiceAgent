package com.ticketing.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticketing.dao.OperationLogDao;
import com.ticketing.entity.OperationLogEntity;
import com.ticketing.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OperationLogServiceImpl implements OperationLogService {

    @Autowired
    private OperationLogDao operationLogDao;

    @Override
    @Async
    public void saveLog(OperationLogEntity logEntity) {
        try {
            logEntity.setCreateTime(java.time.LocalDateTime.now());
            operationLogDao.insert(logEntity);
        } catch (Exception e) {
            log.error("保存操作日志失败", e);
        }
    }

    @Override
    public IPage<OperationLogEntity> getLogPage(int pageNum, int pageSize, Long userId, String module, String type, String startTime, String endTime) {
        Page<OperationLogEntity> page = new Page<>(pageNum, pageSize);
        return operationLogDao.selectLogPage(page, userId, module, type, startTime, endTime);
    }
}
