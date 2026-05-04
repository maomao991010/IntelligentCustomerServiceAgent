package com.ticketing.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class SlowSqlInterceptor implements Interceptor {

    @Value("${ticketings.slow-sql-threshold:1000}")
    private long slowSqlThreshold;

    @Value("${ticketings.slow-sql-log-enabled:true}")
    private boolean slowSqlLogEnabled;

    private final ConcurrentHashMap<String, AtomicLong> slowSqlCountMap = new ConcurrentHashMap<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!slowSqlLogEnabled) {
            return invocation.proceed();
        }

        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        String statementId = mappedStatement.getId();
        SqlCommandType sqlCommandType = mappedStatement.getSqlCommandType();

        long startTime = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= slowSqlThreshold) {
                String sqlId = statementId + ":" + sqlCommandType.name();
                slowSqlCountMap.computeIfAbsent(sqlId, k -> new AtomicLong(0)).incrementAndGet();

                log.warn("[慢SQL] 执行时间: {}ms | 阈值: {}ms | 类型: {} | 语句: {}",
                        elapsed, slowSqlThreshold, sqlCommandType.name(), statementId);

                if (elapsed >= slowSqlThreshold * 5) {
                    log.error("[极慢SQL] 执行时间: {}ms | 语句: {} | 请立即优化!", elapsed, statementId);
                }
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }

    public ConcurrentHashMap<String, AtomicLong> getSlowSqlStats() {
        return slowSqlCountMap;
    }
}
