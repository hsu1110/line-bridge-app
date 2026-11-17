package com.ytec.linebridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 非同步任務執行緒池設定（Webhook 背景處理與訊息非同步發送共用）
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "lineAsyncExecutor")
    public Executor lineAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("line-async-");
        executor.initialize();
        return executor;
    }
}
