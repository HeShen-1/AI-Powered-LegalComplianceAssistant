package com.river.LegalAssistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 * 支持合同分析等CPU密集型异步任务
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    /**
     * 合同分析任务执行器
     */
    @Bean("contractAnalysisTaskExecutor")
    public Executor contractAnalysisTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(2);
        
        // 最大线程数
        executor.setMaxPoolSize(4);
        
        // 队列容量
        executor.setQueueCapacity(50);
        
        // 线程名称前缀
        executor.setThreadNamePrefix("ContractAnalysis-");
        
        // 线程池维护线程所允许的空闲时间
        executor.setKeepAliveSeconds(60);
        
        // 拒绝策略：当线程池已满时，调用线程会直接执行任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        // 设置线程池关闭的时候等待所有任务都完成再继续销毁其他的Bean
        executor.setTaskDecorator(runnable -> {
            // 添加MDC支持（如需要）
            return () -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                    log.error("异步任务执行失败", e);
                    throw e;
                }
            };
        });
        
        executor.initialize();
        
        log.info("合同分析异步任务执行器初始化完成: 核心线程数={}, 最大线程数={}, 队列容量={}", 
            executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    /**
     * 通用异步任务执行器
     */
    @Bean("generalTaskExecutor")
    public Executor generalTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("General-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        
        log.info("通用异步任务执行器初始化完成");
        
        return executor;
    }
}
