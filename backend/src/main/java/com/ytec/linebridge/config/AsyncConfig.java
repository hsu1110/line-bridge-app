package com.ytec.linebridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 非同步任務執行緒池設定（Webhook 背景處理、儀表板並行查詢與訊息非同步發送共用）
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
        // 佇列滿載時立即拒絕，由呼叫端明確處理失敗狀態，避免塞爆 Tomcat 工作執行緒
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 優雅關機：重啟或停止服務時等待進行中與佇列內任務完成，避免狀態卡在 QUEUED
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
