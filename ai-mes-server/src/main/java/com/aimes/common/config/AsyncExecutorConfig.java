package com.aimes.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 列表分页"批量回填查询"专用线程池
 * <p>
 * 适用场景：分页查询主表后,需并行执行 hasProcess / supplierName / categoryName 等多次 IN 查询。
 * <p>
 * 设计原则：
 *  - 核心 8 / 最大 16 / 队列 200,与 HikariCP 默认连接数(10) 配合,避免连接争抢
 *  - 拒绝策略 CallerRunsPolicy:满载时回退到调用线程串行执行,保证不丢任务
 *  - 命名前缀 list-fill- 便于线上线程栈定位
 */
@Configuration
public class AsyncExecutorConfig {

    public static final String LIST_FILL_EXECUTOR = "listFillExecutor";

    @Bean(LIST_FILL_EXECUTOR)
    public Executor listFillExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("list-fill-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
